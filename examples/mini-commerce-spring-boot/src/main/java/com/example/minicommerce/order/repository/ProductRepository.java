package com.example.minicommerce.order.repository;

import com.example.minicommerce.order.dto.OrderProductInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 下单流程读取商品主数据的项目自身持久化端口。 */
@Repository
public class ProductRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderProductInfo> findById(Long id) {
        List<OrderProductInfo> products = jdbcTemplate.query(
            "select id, name, price, active from product where id = ?",
            (resultSet, rowNum) -> new OrderProductInfo(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getBigDecimal("price"),
                resultSet.getBoolean("active")
            ),
            id
        );
        return products.stream().findFirst();
    }
}
