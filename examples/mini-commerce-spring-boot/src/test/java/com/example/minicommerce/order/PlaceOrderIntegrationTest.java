package com.example.minicommerce.order;

import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.example.minicommerce.order.entity.CommerceOrder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证统一订单、按商家拆单、成交快照和整批事务。 */
@SpringBootTest(properties = {
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=${COMMERCE_TEST_JDBC_URL:jdbc:h2:mem:commerce-place-order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${COMMERCE_TEST_JDBC_USER:sa}",
    "spring.datasource.password=${COMMERCE_TEST_JDBC_PASSWORD:}"
})
@AutoConfigureMockMvc
class PlaceOrderIntegrationTest {
    @Autowired EntityDao<CommerceOrder, Long> commerceOrderDao;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EntityMetaRegistry entityMetaRegistry;

    @BeforeEach
    void seed() {
        jdbc.update("delete from commerce_order_item");
        jdbc.update("delete from commerce_order");
        jdbc.update("delete from merchant_product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer(id, display_name, email, merchant_id) values (301, '测试客户', 'test@example.com', 1001)");
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(10, 1001, '商家一商品', 10, true, 0), (20, 1001, '商家二商品', 20, true, 0), "
            + "(30, 2001, '停用商品', 30, false, 0), (40, 2001, '商家二商品', 40, true, 0)");
    }

    @Test
    void 同商家商品合并为一笔订单并保留成交快照() throws Exception {
        String response = mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":10,\"quantity\":1},"
                    + "{\"merchantProductId\":20,\"quantity\":1}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderIds.length()").value(1))
            .andExpect(jsonPath("$.data.totalAmount").value(30.0))
            .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(response, "$.data.orderIds[0]")).longValue();
        assertEquals(1, jdbc.queryForObject("select count(*) from commerce_order", Integer.class));
        jdbc.update("update merchant_product set price=99, name='改名商品' where id=10");
        mvc.perform(post("/api/ent-crud/commerceOrder/detail").header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":%d}".formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.item.merchantId").value(1001))
            .andExpect(jsonPath("$.data.item.totalAmount").value(30.0))
            .andExpect(jsonPath("$.data.item.customer").doesNotExist())
            .andExpect(jsonPath("$.data.item.orderItemList").doesNotExist());

        // 通用详情显式展开；成交快照直接读取明细，不依赖当前商品信息。
        String expanded = mvc.perform(post("/api/ent-crud/commerceOrder/detail")
                .header("Authorization", "Bearer demo-merchant-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"id":%d,"options":{"expandRelations":["customer","orderItemList"]}}
                    """.formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.item.customer.id").value(301))
            .andExpect(jsonPath("$.data.item.customer.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.item.orderItemList.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(expanded, "$.data.item.orderItemList");
        items.forEach(item -> assertEquals(orderId, ((Number) item.get("orderId")).longValue()));
        var snapshot = items.stream()
            .filter(item -> ((Number) item.get("merchantProductId")).longValue() == 10L).findFirst().orElseThrow();
        assertEquals("商家一商品", snapshot.get("productName"));
        assertEquals(0, BigDecimal.TEN.compareTo(new BigDecimal(snapshot.get("unitPrice").toString())));
    }

    @Test
    void 业务下单可赋予默认归属但普通DAO局部更新不能转移归属() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":10,\"quantity\":1}]")))
            .andExpect(status().isOk());
        Long orderId = jdbc.queryForObject("select max(id) from commerce_order", Long.class);
        var binder = new DefaultCommandPayloadBinder();
        for (String field : List.of("customerId", "merchantId")) {
            var patch = binder.bindUpdatePatch(Map.of("id", orderId, field, 999L),
                CommerceOrder.class, entityMetaRegistry.getEntityMeta(CommerceOrder.class));
            assertThrows(ValidationException.class, () -> commerceOrderDao.updateById(orderId, patch));
        }
        CommerceOrder order = commerceOrderDao.findById(orderId).orElseThrow();
        assertEquals(301L, order.getCustomerId());
        assertEquals(1001L, order.getMerchantId());
    }

    @Test
    void 多商家下单只生成各商家订单且明细金额与归属一致() throws Exception {
        String response = mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":10,\"quantity\":2},"
                    + "{\"merchantProductId\":40,\"quantity\":1}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderIds.length()").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(60.0))
            .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(response, "$.data.orderIds");
        assertEquals(ids.stream().map(Number::longValue).toList(),
            jdbc.queryForList("select id from commerce_order order by id", Long.class));
        List<CommerceOrder> orders = commerceOrderDao.findAllById(ids.stream().map(Number::longValue).toList());
        assertEquals(List.of(1001L, 2001L), orders.stream().map(CommerceOrder::getMerchantId).sorted().toList());
        for (CommerceOrder order : orders) {
            assertEquals(301L, order.getCustomerId());
            BigDecimal amount = jdbc.queryForObject("select sum(line_amount) from commerce_order_item where order_id=?",
                BigDecimal.class, order.getId());
            assertEquals(0, order.getTotalAmount().compareTo(amount));
        }
        assertEquals(0, jdbc.queryForObject("""
            select count(*) from commerce_order_item i join commerce_order o on o.id=i.order_id
            join merchant_product p on p.id=i.merchant_product_id where o.merchant_id<>p.merchant_id
            """, Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from commerce_order_item", Integer.class));
    }

    @Test
    void 小数金额精确计算且商品改价后保留成交金额() throws Exception {
        jdbc.update("update merchant_product set price=0.10 where id=10");
        jdbc.update("update merchant_product set price=19.99 where id=40");
        mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":10,\"quantity\":3},"
                    + "{\"merchantProductId\":40,\"quantity\":2}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderIds.length()").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(40.28));

        jdbc.update("update merchant_product set price=99.99, name='改价后商品' where id in (10,40)");
        assertEquals(0, new BigDecimal("40.28").compareTo(jdbc.queryForObject(
            "select sum(total_amount) from commerce_order", BigDecimal.class)));
        assertEquals(0, new BigDecimal("0.10").compareTo(jdbc.queryForObject(
            "select unit_price from commerce_order_item where merchant_product_id=10", BigDecimal.class)));
        assertEquals(0, new BigDecimal("0.30").compareTo(jdbc.queryForObject(
            "select line_amount from commerce_order_item where merchant_product_id=10", BigDecimal.class)));
        assertEquals(0, new BigDecimal("39.98").compareTo(jdbc.queryForObject(
            "select line_amount from commerce_order_item where merchant_product_id=40", BigDecimal.class)));
        assertEquals(0, jdbc.queryForObject("""
            select count(*) from commerce_order o
            where o.total_amount<>(select sum(i.line_amount) from commerce_order_item i where i.order_id=o.id)
            """, Integer.class));
    }

    @ParameterizedTest
    @CsvSource({"commerce_order,total_amount", "commerce_order_item,line_amount"})
    void 后续商家写入失败时回滚全部订单和明细(String table, String amountField) throws Exception {
        // 分别在第二个商家的订单、明细写入时触发约束，验证此前已写入的数据全部回滚。
        jdbc.execute("alter table `%s` add constraint test_order_amount check (%s <= 30)"
            .formatted(table, amountField));
        try {
            mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                    .content(command("[{\"merchantProductId\":10,\"quantity\":1},"
                        + "{\"merchantProductId\":40,\"quantity\":1}]")))
                .andExpect(status().is5xxServerError());
            assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order_item", Integer.class));
        } finally {
            jdbc.execute("alter table `%s` drop constraint test_order_amount".formatted(table));
        }
    }

    @Test
    void 下单拒绝停用或不存在的商家商品() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":30,\"quantity\":1}]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MERCHANT_PRODUCT_INACTIVE"));
        mvc.perform(post("/api/orders").header("Authorization", "Bearer demo-customer-a").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":999,\"quantity\":1}]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MERCHANT_PRODUCT_NOT_FOUND"));
        assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order_item", Integer.class));
    }

    private String command(String items) {
        return "{\"items\":" + items + "}";
    }
}
