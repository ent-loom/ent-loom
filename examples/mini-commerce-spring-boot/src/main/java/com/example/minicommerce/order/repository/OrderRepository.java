package com.example.minicommerce.order.repository;

import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 下单事务的专用持久化：当前受授权 DAO 尚不支持创建和关联读取。
 * 调用方须先完成业务授权，并在读取明细前校验所属订单的访问范围。
 */
@Repository
@RequiredArgsConstructor
public class OrderRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public boolean customerExists(Long customerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from customer where id=?)",
            Boolean.class, customerId));
    }

    public List<MerchantProduct> findProducts(Set<Long> ids) {
        return namedJdbc.query("select id, merchant_id, name, price, active, version from merchant_product where id in (:ids)",
            Map.of("ids", ids), BeanPropertyRowMapper.newInstance(MerchantProduct.class));
    }

    public Long insert(Order order) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "insert into `order` (customer_id, merchant_id, status, total_amount, created_at) values (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, order.getCustomerId());
            statement.setLong(2, order.getMerchantId());
            statement.setString(3, order.getStatus().name());
            statement.setBigDecimal(4, order.getTotalAmount());
            statement.setObject(5, order.getCreatedAt());
            return statement;
        }, key);
        Long id = java.util.Objects.requireNonNull(key.getKey(), "订单主键未生成").longValue();
        order.setId(id);
        return id;
    }

    public void insertItems(List<OrderItem> items) {
        jdbc.batchUpdate("insert into order_item (order_id, merchant_product_id, merchant_id, product_name, "
                + "unit_price, quantity, line_amount) values (?, ?, ?, ?, ?, ?, ?)",
            items, items.size(), (statement, item) -> {
                statement.setLong(1, item.getOrderId());
                statement.setLong(2, item.getMerchantProductId());
                statement.setLong(3, item.getMerchantId());
                statement.setString(4, item.getProductName());
                statement.setBigDecimal(5, item.getUnitPrice());
                statement.setInt(6, item.getQuantity());
                statement.setBigDecimal(7, item.getLineAmount());
            });
    }

    public List<OrderItem> findItems(Long orderId) {
        return jdbc.query("select id, order_id, merchant_product_id, merchant_id, product_name, unit_price, quantity, "
            + "line_amount from order_item where order_id=? order by id",
            BeanPropertyRowMapper.newInstance(OrderItem.class), orderId);
    }
}
