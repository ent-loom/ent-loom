package com.example.minicommerce.order.repository;

import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 订单聚合的最小 JDBC 持久化端口。 */
@Repository
public class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 在调用方事务内新增订单及明细；回填订单 ID 和明细的订单关联，明细自增 ID 不回填。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long insert(Order order, List<OrderItem> items) {
        Long orderId = insertOrder(order);
        order.setId(orderId);
        items.forEach(item -> item.setOrderId(orderId));
        insertItems(items);
        return orderId;
    }

    private Long insertOrder(Order order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into commerce_order(customer_id, status, total_amount, created_at) values (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, order.getCustomerId());
            statement.setString(2, order.getStatus().name());
            statement.setBigDecimal(3, order.getTotalAmount());
            statement.setTimestamp(4, Timestamp.valueOf(order.getCreatedAt()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("订单写入后未返回主键");
        }
        return key.longValue();
    }

    private void insertItems(List<OrderItem> items) {
        jdbcTemplate.batchUpdate(
            "insert into commerce_order_item(order_id, product_id, product_name, unit_price, quantity, line_amount) "
                + "values (?, ?, ?, ?, ?, ?)",
            items,
            items.size(),
            (statement, item) -> {
                statement.setLong(1, item.getOrderId());
                statement.setLong(2, item.getProductId());
                statement.setString(3, item.getProductName());
                statement.setBigDecimal(4, item.getUnitPrice());
                statement.setInt(5, item.getQuantity());
                statement.setBigDecimal(6, item.getLineAmount());
            }
        );
    }

    public Optional<OrderDetail> findDetail(Long orderId) {
        List<OrderHeader> headers = jdbcTemplate.query(
            "select o.id, o.customer_id, c.display_name, o.status, o.total_amount, o.created_at "
                + "from commerce_order o join customer c on c.id = o.customer_id where o.id = ?",
            (resultSet, rowNum) -> new OrderHeader(
                resultSet.getLong("id"),
                resultSet.getLong("customer_id"),
                resultSet.getString("display_name"),
                OrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("total_amount"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
            ),
            orderId
        );
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        OrderHeader header = headers.get(0);
        List<OrderDetail.Item> items = jdbcTemplate.query(
            "select product_id, product_name, unit_price, quantity, line_amount "
                + "from commerce_order_item where order_id = ? order by id",
            (resultSet, rowNum) -> new OrderDetail.Item(
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getBigDecimal("unit_price"),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("line_amount")
            ),
            orderId
        );
        return Optional.of(new OrderDetail(
            header.id(),
            header.customerId(),
            header.customerName(),
            header.status(),
            header.totalAmount(),
            header.createdAt(),
            items
        ));
    }

    private record OrderHeader(
        Long id,
        Long customerId,
        String customerName,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
    ) {
    }
}
