package com.example.minicommerce.order;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.entity.CommerceOrder;
import com.example.minicommerce.order.entity.CommerceOrderItem;
import com.example.minicommerce.order.enums.CommerceOrderStatus;
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
    "spring.config.import=classpath:demo.yml,classpath:crud-policies.yml",
    "spring.datasource.url=jdbc:h2:mem:commerce-order-dao;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password="
})
class CommerceOrderDaoIntegrationTest {
    @Autowired EntityDao<CommerceOrderItem, Long> commerceOrderItemDao;
    @Autowired EntityDao<CommerceOrder, Long> commerceOrderDao;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from commerce_order_item");
        jdbc.update("delete from commerce_order");
        jdbc.update("delete from customer");
        jdbc.update("insert into customer(id,display_name,email,merchant_id) values (1,'客户甲','a@example.com',1001)");
    }

    @Test
    void DAO使用统一订单和明细约定表并保持关系字段() {
        CommerceOrder order = newOrder();
        order.setCustomer(new Customer());
        order.setOrderItemList(java.util.List.of(new CommerceOrderItem()));
        commerceOrderDao.insert(order);
        assertNotNull(order.getId());
        assertEquals(1001L, commerceOrderDao.findById(order.getId()).orElseThrow().getMerchantId());
        assertEquals(0, jdbc.queryForObject("select count(*) from commerce_order_item", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from customer", Integer.class));
        assertNull(commerceOrderDao.findById(order.getId()).orElseThrow().getCustomer());
        assertNull(commerceOrderDao.findById(order.getId()).orElseThrow().getOrderItemList());

        CommerceOrderItem item = new CommerceOrderItem();
        item.setOrderId(order.getId());
        item.setMerchantProductId(10L);
        item.setProductName("商家一商品");
        item.setUnitPrice(BigDecimal.TEN);
        item.setQuantity(1);
        item.setLineAmount(BigDecimal.TEN);
        commerceOrderItemDao.insert(item);
        assertNotNull(item.getId());
        assertEquals(order.getId(), commerceOrderItemDao.findById(item.getId()).orElseThrow().getOrderId());
    }

    private CommerceOrder newOrder() {
        CommerceOrder order = new CommerceOrder();
        order.setCustomerId(1L);
        order.setMerchantId(1001L);
        order.setStatus(CommerceOrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }
}
