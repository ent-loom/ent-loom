package com.example.minicommerce.commerce;

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

    public Optional<CustomerSnapshot> findById(Long id) {
        List<CustomerSnapshot> customers = jdbcTemplate.query(
            "select id, display_name from customer where id = ?",
            (resultSet, rowNum) -> new CustomerSnapshot(
                resultSet.getLong("id"),
                resultSet.getString("display_name")
            ),
            id
        );
        return customers.stream().findFirst();
    }
}
