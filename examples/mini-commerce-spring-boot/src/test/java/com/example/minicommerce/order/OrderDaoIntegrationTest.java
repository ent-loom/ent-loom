package com.example.minicommerce.order;

import com.example.minicommerce.order.dao.MerchantOrderDao;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.entity.MerchantOrder;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证商家商品下单、按商家拆单以及价格快照。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-handler;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class OrderDaoIntegrationTest {
    @Autowired OrderItemDao orderItems;
    @Autowired OrderDao orders;
    @Autowired MerchantOrderDao merchantOrders;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from order_item");
        jdbc.update("delete from merchant_order");
        jdbc.update("delete from `order`");
        jdbc.update("delete from merchant_product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer values (1, '测试客户', 'test@example.com')");
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(10, 1001, '商家一商品', 10, true, 0), (20, 1001, '商家二商品', 20, true, 0), "
            + "(30, 2001, '停用商品', 30, false, 0)");
    }

    @Test
    void 文档注册商家商品和商家销售订单且不再暴露平台商品() throws Exception {
        mvc.perform(get("/api/ent-doc/contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[*].resourceCode",
                org.hamcrest.Matchers.containsInAnyOrder("customer", "customerAddress", "order", "orderItem",
                    "merchantProduct", "merchantPoster", "merchantOrder")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'merchantProduct')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "merchantId", "name", "price", "active", "version")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'orderItem')].fields[*].property",
                org.hamcrest.Matchers.hasItems("merchantOrderId", "merchantProductId", "merchantId", "productName", "unitPrice")));
    }

    @Test
    void DAO使用商家订单和明细约定表并保持关系字段() {
        Order order = new Order();
        order.setCustomerId(1L);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        order.setOrderItemList(java.util.List.of(new OrderItem()));
        orders.insert(order);
        assertNotNull(order.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
        assertNull(orders.findById(order.getId()).orElseThrow().getOrderItemList());

        MerchantOrder merchantOrder = new MerchantOrder();
        merchantOrder.setOrderId(order.getId());
        merchantOrder.setCustomerId(1L);
        merchantOrder.setMerchantId(1001L);
        merchantOrder.setStatus(OrderStatus.CREATED);
        merchantOrder.setTotalAmount(BigDecimal.TEN);
        merchantOrder.setCreatedAt(LocalDateTime.now());
        merchantOrders.insert(merchantOrder);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setMerchantOrderId(merchantOrder.getId());
        item.setMerchantProductId(10L);
        item.setMerchantId(1001L);
        item.setProductName("商家一商品");
        item.setUnitPrice(BigDecimal.TEN);
        item.setQuantity(1);
        item.setLineAmount(BigDecimal.TEN);
        orderItems.insert(item);
        assertNotNull(item.getId());
        assertEquals(item.getId(), orderItems.findByOrderId(order.getId()).get(0).getId());
    }

    @Test
    void 下单按商家拆分销售订单并保留成交快照() throws Exception {
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":10,\"quantity\":1},"
                    + "{\"merchantProductId\":20,\"quantity\":1}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.merchantOrderIds.length()").value(1))
            .andExpect(jsonPath("$.data.totalAmount").value(30.0));
        Long orderId = jdbc.queryForObject("select max(id) from `order`", Long.class);
        Long merchantOrderId = jdbc.queryForObject("select id from merchant_order where order_id=?", Long.class, orderId);
        jdbc.update("update merchant_product set price=99, name='改名商品' where id=10");
        mvc.perform(post("/api/ent-crud/order/detail").contentType(MediaType.APPLICATION_JSON)
                .content(("{\"options\":{\"resultMode\":\"ENTITY\",\"filter\":{\"id\":%d},"
                    + "\"expandRelations\":[\"customer\",\"orderItemList\",\"merchantOrderList\"]}}" ).formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.item.orderItemList[?(@.merchantProductId == 10)].productName",
                org.hamcrest.Matchers.contains("商家一商品")))
            .andExpect(jsonPath("$.data.item.orderItemList[?(@.merchantProductId == 10)].unitPrice",
                org.hamcrest.Matchers.contains(10.0)))
            .andExpect(jsonPath("$.data.item.merchantOrderList[0].id").value(merchantOrderId));
    }

    @Test
    void 多商家下单创建多个销售订单() throws Exception {
        jdbc.update("insert into merchant_product(id, merchant_id, name, price, active, version) values "
            + "(40, 2001, '商家二商品', 40, true, 0)");
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":10,\"quantity\":1},"
                    + "{\"merchantProductId\":40,\"quantity\":1}]")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.merchantOrderIds.length()").value(2));
        assertEquals(2, jdbc.queryForObject("select count(*) from merchant_order", Integer.class));
    }

    @Test
    void 下单校验数量并拒绝停用或不存在的商家商品() throws Exception {
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":30,\"quantity\":1}]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MERCHANT_PRODUCT_INACTIVE"));
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content(command("[{\"merchantProductId\":999,\"quantity\":1}]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MERCHANT_PRODUCT_NOT_FOUND"));
        assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
    }

    @Test
    void 商家商品通用写入路由关闭() throws Exception {
        mvc.perform(post("/api/ent-crud/merchantProduct/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":10,\"name\":\"越权\"}}"))
            .andExpect(status().isNotFound());
    }

    private String command(String items) {
        return "{\"payload\":{\"customerId\":1,\"items\":" + items + "}}";
    }
}
