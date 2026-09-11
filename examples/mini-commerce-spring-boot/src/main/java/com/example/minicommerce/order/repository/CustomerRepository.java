package com.example.minicommerce.order.repository;

import com.example.minicommerce.order.dto.OrderCustomerInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 下单流程读取客户主数据的项目自身持久化端口。 */
@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OrderCustomerInfo> findById(Long id) {
        List<OrderCustomerInfo> customers = jdbcTemplate.query(
            "select id, display_name from customer where id = ?",
            (resultSet, rowNum) -> new OrderCustomerInfo(
                resultSet.getLong("id"),
                resultSet.getString("display_name")
            ),
            id
        );
        return customers.stream().findFirst();
    }
}
