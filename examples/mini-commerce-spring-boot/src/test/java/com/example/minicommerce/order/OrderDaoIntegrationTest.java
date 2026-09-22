package com.example.minicommerce.order;

import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 从公开入口经过真实 Gateway、场景注册、DAO 与事务代理；测试自身不包裹事务。 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:commerce-handler;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.sql.init.mode=always"
})
@ActiveProfiles("example")
@AutoConfigureMockMvc
class OrderDaoIntegrationTest {
    @Autowired OrderItemDao orderItems;
    @Autowired OrderDao orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from order_item");
        jdbc.update("delete from `order`");
        jdbc.update("delete from product");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer values (1, '测试客户', 'test@example.com')");
        jdbc.update("insert into product values (10, '商品一', 10, true), (20, '商品二', 20, true), (30, '停用商品', 30, false)");
    }

    @Test
    void 配置装配文档白名单且订单仍不进入公共文档() throws Exception {
        mvc.perform(get("/api/ent-doc/contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[*].resourceCode",
                org.hamcrest.Matchers.containsInAnyOrder("product", "customer")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'customer')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "displayName", "email")));
    }

    @Test
    void shouldUseConventionTableNamesForDaoAndCustomSql() {
        Order order = new Order();
        order.setCustomerId(1L);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        orders.insert(order);
        assertNotNull(order.getId());

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(10L);
        item.setProductName("商品一");
        item.setUnitPrice(BigDecimal.TEN);
        item.setQuantity(1);
        item.setLineAmount(BigDecimal.TEN);
        orderItems.insert(item);
        assertNotNull(item.getId());

        assertEquals(1, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from order_item", Integer.class));
        assertEquals(item.getId(), orderItems.findByOrderId(order.getId()).get(0).getId());
    }

    @Test
    void shouldPersistGeneratedKeysAndAssembleSnapshotDetail() throws Exception {
        placeOrder(1);
        Long orderId = jdbc.queryForObject("select max(id) from `order`", Long.class);
        jdbc.update("update product set price=99, name='改名商品' where id=10");
        mvc.perform(post("/api/ent-crud/order/detail/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"filter\":{\"id\":" + orderId + "}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.item.customerName").value("测试客户"))
            .andExpect(jsonPath("$.data.item.totalAmount").value(30.0))
            .andExpect(jsonPath("$.data.item.items[0].productName").value("商品一"))
            .andExpect(jsonPath("$.data.item.items[0].unitPrice").value(10.0));
        assertTrue(orderItems.findByOrderId(orderId).stream()
            .allMatch(item -> item.getId() != null && orderId.equals(item.getOrderId())));
        mvc.perform(post("/api/ent-crud/order/detail/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"filter\":{\"id\":-1}}}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void secondItemFailureShouldRollbackOrderAndFirstItem() throws Exception {
        jdbc.execute("alter table order_item add constraint reject_second check(product_id <> 20)");
        try {
            placeOrder(1, status().isInternalServerError());
            assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
        } finally {
            jdbc.execute("alter table order_item drop constraint reject_second");
        }
    }

    @Test
    void handlerAlsoValidatesCommandPayload() throws Exception {
        placeOrder(0, status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
    }

    @Test
    void saleableScenePreservesPagingSortingAndIntersectsCallerFilters() throws Exception {
        mvc.perform(post("/api/ent-crud/product/page/saleable").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"options":{"page":1,"limit":1,"sorts":[{"field":"price","direction":"DESC"}]}}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(2))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(20));
        mvc.perform(post("/api/ent-crud/product/page/saleable").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"options":{"filters":[{"field":"active","op":"EQ","value":false}]}}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(0));
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(3));
    }

    @Test
    void orderOnlyExposesAuthorizedBusinessScenes() throws Exception {
        mvc.perform(post("/api/ent-crud/order/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"customerId\":1}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/order/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":1,\"status\":\"CREATED\"}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/order/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":1}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/order/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/ent-crud/order/action/unknown").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{}}"))
            .andExpect(status().isNotFound());
    }

    private void placeOrder(int quantity) throws Exception {
        placeOrder(quantity, status().isOk());
    }

    private void placeOrder(int quantity, org.springframework.test.web.servlet.ResultMatcher status) throws Exception {
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"customerId\":1,\"items\":["
                    + "{\"productId\":10,\"quantity\":" + quantity + "},"
                    + "{\"productId\":20,\"quantity\":1}]}}"))
            .andExpect(status);
    }
}
