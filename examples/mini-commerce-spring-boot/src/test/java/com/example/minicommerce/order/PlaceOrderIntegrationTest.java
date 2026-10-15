package com.example.minicommerce.order;

import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.example.minicommerce.order.entity.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证可信客户下单、按商家拆单、事务边界、成交快照及双端详情隔离。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-place-order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class PlaceOrderIntegrationTest {
    @Autowired EntityDao<Order, Long> orderDao;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EntityMetaRegistry metas;
    @Autowired ObjectMapper json;

    @BeforeEach
    void seed() {
        jdbc.update("delete from order_item");
        jdbc.update("delete from `order`");
        jdbc.update("delete from merchant_product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer values (301, '测试客户', 'test@example.com')");
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(10, 1001, '商家一商品', 10, true, 0), (20, 1001, '同商家商品', 20, true, 0), "
            + "(30, 2001, '停用商品', 30, false, 0), (40, 2001, '商家二商品', 40, true, 0)");
    }

    @Test
    void 同商家商品生成一张订单且客户与商家详情保留成交快照() throws Exception {
        var response = place("[{\"merchantProductId\":10,\"quantity\":1},"
            + "{\"merchantProductId\":20,\"quantity\":1}]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderIds.length()").value(1))
            .andExpect(jsonPath("$.data.totalAmount").value(30.0))
            .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(response).at("/data/orderIds/0").asLong();
        assertEquals(1, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from order_item where order_id=?", Integer.class, orderId));
        jdbc.update("update merchant_product set price=99, name='改名商品' where id=10");
        for (String[] entry : List.of(new String[]{"consumer", "demo-customer-a"},
            new String[]{"merchant", "demo-merchant-a"})) {
            mvc.perform(post("/api/" + entry[0] + "/orders/detail").header("Authorization", "Bearer " + entry[1])
                    .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.id").value(orderId))
                .andExpect(jsonPath("$.data.order.customerId").value(301))
                .andExpect(jsonPath("$.data.order.merchantId").value(1001))
                .andExpect(jsonPath("$.data.items[?(@.merchantProductId == 10)].productName",
                    org.hamcrest.Matchers.contains("商家一商品")))
                .andExpect(jsonPath("$.data.items[?(@.merchantProductId == 10)].unitPrice",
                    org.hamcrest.Matchers.contains(10.0)));
        }
    }

    @Test
    void 下单默认赋予双重归属且普通DAO局部更新不能转移归属() throws Exception {
        place("[{\"merchantProductId\":10,\"quantity\":1}]").andExpect(status().isOk());
        Long orderId = jdbc.queryForObject("select max(id) from `order`", Long.class);
        var binder = new DefaultCommandPayloadBinder();
        for (String field : List.of("customerId", "merchantId")) {
            var patch = binder.bindUpdatePatch(Map.of("id", orderId, field, 999L),
                Order.class, metas.getEntityMeta(Order.class));
            assertThrows(ValidationException.class, () -> orderDao.updateById(orderId, patch));
        }
        var order = orderDao.findById(orderId).orElseThrow();
        assertEquals(301L, order.getCustomerId());
        assertEquals(1001L, order.getMerchantId());
    }

    @Test
    void 伪造请求头不能改变下单客户和商品对应商家() throws Exception {
        mvc.perform(post("/api/consumer/orders/place").header("Authorization", "Bearer demo-customer-a")
                .header("X-Customer-Id", "302").header("X-Merchant-Id", "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"merchantProductId\":10,\"quantity\":1}]}"))
            .andExpect(status().isOk());
        assertEquals(301L, jdbc.queryForObject("select customer_id from `order`", Long.class));
        assertEquals(1001L, jdbc.queryForObject("select merchant_id from `order`", Long.class));
    }

    @Test
    void 多商家下单直接创建独立订单且明细归属和各单金额正确() throws Exception {
        var response = place("[{\"merchantProductId\":10,\"quantity\":2},"
            + "{\"merchantProductId\":40,\"quantity\":1}]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderIds.length()").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(60.0))
            .andReturn().getResponse().getContentAsString();
        var orderIds = json.readTree(response).at("/data/orderIds");
        assertNotEquals(orderIds.get(0).asLong(), orderIds.get(1).asLong());
        assertEquals(2, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from order_item", Integer.class));
        for (var id : orderIds) {
            var order = orderDao.findById(id.asLong()).orElseThrow();
            assertEquals(301L, order.getCustomerId());
            assertEquals(0, order.getTotalAmount().compareTo(order.getMerchantId() == 1001L
                ? new BigDecimal("20") : new BigDecimal("40")));
            assertEquals(order.getMerchantId(), jdbc.queryForObject(
                "select merchant_id from order_item where order_id=?", Long.class, order.getId()));
        }
    }

    @Test
    void 后一个商家明细保存失败时所有订单和明细整体回滚() throws Exception {
        jdbc.execute("alter table order_item add constraint test_line_amount check (line_amount <= 10)");
        try {
            place("[{\"merchantProductId\":10,\"quantity\":1},"
                + "{\"merchantProductId\":40,\"quantity\":1}]").andExpect(status().is5xxServerError());
            assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
        } finally {
            jdbc.execute("alter table order_item drop constraint test_line_amount");
        }
    }

    @Test
    void 客户与商家均不能读取他人订单明细且不存在时语义一致() throws Exception {
        place("[{\"merchantProductId\":10,\"quantity\":1}]").andExpect(status().isOk());
        Long orderId = jdbc.queryForObject("select max(id) from `order`", Long.class);
        for (String[] entry : List.of(new String[]{"consumer", "demo-customer-b"},
            new String[]{"merchant", "demo-merchant-b"})) {
            for (long id : new long[]{orderId, Long.MAX_VALUE}) {
                mvc.perform(post("/api/" + entry[0] + "/orders/detail").header("Authorization", "Bearer " + entry[1])
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + id + "}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
            }
        }
    }

    @Test
    void 下单校验数量并拒绝停用不存在及重复商品() throws Exception {
        for (String[] entry : List.of(
            new String[]{"[{\"merchantProductId\":30,\"quantity\":1}]", "MERCHANT_PRODUCT_INACTIVE"},
            new String[]{"[{\"merchantProductId\":999,\"quantity\":1}]", "MERCHANT_PRODUCT_NOT_FOUND"},
            new String[]{"[{\"merchantProductId\":10,\"quantity\":0}]", "VALIDATION_ERROR"},
            new String[]{"[{\"merchantProductId\":10,\"quantity\":1},{\"merchantProductId\":10,\"quantity\":1}]",
                "DUPLICATE_MERCHANT_PRODUCT"})) {
            place(entry[0]).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(entry[1]));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
    }

    private org.springframework.test.web.servlet.ResultActions place(String items) throws Exception {
        return mvc.perform(post("/api/consumer/orders/place").header("Authorization", "Bearer demo-customer-a")
            .contentType(MediaType.APPLICATION_JSON).content("{\"items\":" + items + "}"));
    }
}
