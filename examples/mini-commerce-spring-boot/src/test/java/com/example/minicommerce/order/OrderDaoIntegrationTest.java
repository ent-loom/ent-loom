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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证订单 DAO 的约定表名、关系字段及实体持久化边界。 */
@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:commerce-order-dao;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
class OrderDaoIntegrationTest {
    @Autowired OrderItemDao orderItems;
    @Autowired OrderDao orders;
    @Autowired MerchantOrderDao merchantOrders;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from order_item");
        jdbc.update("delete from merchant_order");
        jdbc.update("delete from `order`");
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
}
