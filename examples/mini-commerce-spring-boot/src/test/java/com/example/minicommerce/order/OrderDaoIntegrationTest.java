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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 从公开入口经过真实 Gateway、场景注册、DAO 与事务代理；测试自身不包裹事务。 */
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
    void 主数据创建回填主键且客户允许更新但拒绝删除() throws Exception {
        mvc.perform(post("/api/ent-crud/product/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"name\":\"新增商品\",\"price\":19.90,\"active\":true}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isNumber());
        mvc.perform(post("/api/ent-crud/customer/create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"displayName\":\"新增客户\",\"email\":\"crud@example.com\"}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isNumber());
        Long customerId = jdbc.queryForObject("select id from customer where email='crud@example.com'", Long.class);
        mvc.perform(post("/api/ent-crud/customer/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":%d,\"displayName\":\"已更新客户\"}}".formatted(customerId)))
            .andExpect(status().isOk());
        assertEquals("已更新客户", jdbc.queryForObject("select display_name from customer where id=?", String.class, customerId));
        mvc.perform(post("/api/ent-crud/customer/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":%d}}".formatted(customerId)))
            .andExpect(status().isForbidden());
        assertEquals(1, jdbc.queryForObject("select count(*) from customer where id=?", Integer.class, customerId));
    }

    @Test
    void 文档默认复用Meta全部实体及字段() throws Exception {
        mvc.perform(get("/api/ent-doc/contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entities[*].resourceCode",
                org.hamcrest.Matchers.containsInAnyOrder("product", "customer", "order", "orderItem")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'customer')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "displayName", "email")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'product')].fields[*].property",
                org.hamcrest.Matchers.containsInAnyOrder("id", "name", "price", "active")))
            .andExpect(jsonPath("$.entities[?(@.resourceCode == 'orderItem')].fields[*].property",
                org.hamcrest.Matchers.hasItems("orderId", "productName", "unitPrice")));
    }

    @Test
    void shouldUseConventionTableNamesForDaoAndCustomSql() {
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
        Order loaded = orders.findById(order.getId()).orElseThrow();
        loaded.setOrderItemList(orderItems.findByOrderId(order.getId()));
        assertEquals(item.getId(), loaded.getOrderItemList().getFirst().getId());
        orders.update(loaded);
        assertEquals(1, jdbc.queryForObject("select count(*) from order_item", Integer.class));
    }

    @Test
    void 默认详情展开客户和明细且保留价格快照() throws Exception {
        placeOrder(1);
        mvc.perform(post("/api/ent-crud/customer/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"id\":1}}"))
            .andExpect(status().isForbidden());
        Long orderId = jdbc.queryForObject("select max(id) from `order`", Long.class);
        jdbc.update("update product set price=99, name='改名商品' where id=10");
        mvc.perform(post("/api/ent-crud/order/detail").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"options":{"resultMode":"ENTITY","filter":{"id":%d},"expandRelations":["customer","orderItemList"]}}
                    """.formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.item.customer.displayName").value("测试客户"))
            .andExpect(jsonPath("$.data.item.totalAmount").value(30.0))
            .andExpect(jsonPath("$.data.item.orderItemList.length()").value(2))
            .andExpect(jsonPath("$.data.item.orderItemList[?(@.productId == 10)].productName",
                org.hamcrest.Matchers.contains("商品一")))
            .andExpect(jsonPath("$.data.item.orderItemList[?(@.productId == 10)].unitPrice",
                org.hamcrest.Matchers.contains(10.0)));
        assertTrue(orderItems.findByOrderId(orderId).stream()
            .allMatch(item -> item.getId() != null && orderId.equals(item.getOrderId())));
        mvc.perform(post("/api/ent-crud/order/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":{\"filter\":{\"id\":-1}}}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
        mvc.perform(post("/api/ent-crud/order/detail").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"options":{"resultMode":"ENTITY","filter":{"id":%d,"customerId":-1},"expandRelations":["orderItemList"]}}
                    """.formatted(orderId)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
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
    void 下单校验数量且拒绝停用商品() throws Exception {
        placeOrder(0, status().isBadRequest());
        mvc.perform(post("/api/ent-crud/order/action/place").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"payload":{"customerId":1,"items":[{"productId":30,"quantity":1}]}}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        assertEquals(0, jdbc.queryForObject("select count(*) from `order`", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_item", Integer.class));
    }

    @Test
    void 默认消费者分页强制启用状态且保留排序和分页() throws Exception {
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"options":{"filter":{"active":true},"page":1,"limit":1,"sorts":[{"field":"price","direction":"DESC"}]}}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(2))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(20));
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"options":{"filter":{"active":false}}}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(0))
            .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(post("/api/ent-crud/product/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(2));
    }

    @Test
    void 订单允许默认读取但拒绝直接写入及未知业务动作() throws Exception {
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
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total").value(0));
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
