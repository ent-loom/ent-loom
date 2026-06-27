package com.entloom.crud.engine.jdbc.dialect;

import java.util.Locale;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC 数据库产品名到方言的解析器。
 */
public final class JdbcDialectResolver {
    private JdbcDialectResolver() {
    }

    /**
     * 基于 JdbcTemplate 当前连接的数据库产品名解析方言。
     */
    /**
     * 解析数据库方言。
     */
    public static JdbcDialect resolve(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            return StandardJdbcDialect.GENERIC;
        }
        try {
            String productName = jdbcTemplate.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
            return resolve(productName);
        } catch (Exception ex) {
            return StandardJdbcDialect.GENERIC;
        }
    }

    /**
     * 基于数据库产品名解析方言。
     */
    /**
     * 解析数据库方言。
     */
    public static JdbcDialect resolve(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return StandardJdbcDialect.GENERIC;
        }
        String normalizedProductName = productName.toLowerCase(Locale.ROOT);
        if (normalizedProductName.contains("mariadb") || normalizedProductName.contains("mysql")) {
            return StandardJdbcDialect.MYSQL;
        }
        if (normalizedProductName.contains("postgresql")) {
            return StandardJdbcDialect.POSTGRESQL;
        }
        if (normalizedProductName.contains("oracle")) {
            return StandardJdbcDialect.ORACLE;
        }
        if (normalizedProductName.contains("sql server")) {
            return StandardJdbcDialect.SQL_SERVER;
        }
        if (normalizedProductName.contains("h2")) {
            return StandardJdbcDialect.H2;
        }
        return StandardJdbcDialect.GENERIC;
    }
}
