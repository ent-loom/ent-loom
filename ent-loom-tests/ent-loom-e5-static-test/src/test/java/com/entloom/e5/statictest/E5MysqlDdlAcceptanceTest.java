package com.entloom.e5.statictest;

import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.spring.EntDdlSpringOptions;
import com.entloom.ddl.starter.EntDdlAutoConfiguration;
import java.util.Collections;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E5.2：复用 CustomerProfile 验证 MySQL 8 DDL 执行结果。
 *
 * <p>仅在 mysql-integration profile 下连接本机隔离 MySQL 实例；每次使用随机 schema 并在结束后删除。</p>
 */
class E5MysqlDdlAcceptanceTest {

    @Test
    @DisplayName("CustomerProfile 应在 MySQL 8 创建字段、主键和唯一索引")
    void shouldCreateCustomerProfileInMysql8() {
        Assumptions.assumeTrue(Boolean.parseBoolean(
                System.getProperty("entloom.e5.mysql.integration", "false")),
            "未启用 mysql-integration profile");

        String url = requiredProperty("entloom.e5.mysql.url");
        String username = requiredProperty("entloom.e5.mysql.username");
        String password = mysqlPassword();
        String schema = "entloom_e5_e52_" + UUID.randomUUID().toString().replace("-", "");
        String table = "customer_profile";

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(EntDdlSpringOptions.class, () -> options(schema));
        context.register(EntDdlAutoConfiguration.class);
        Throwable primaryFailure = null;
        try {
            context.refresh();

            assertEquals(1, count(jdbcTemplate,
                "SELECT COUNT(*) FROM information_schema.tables"
                    + " WHERE table_schema = ? AND table_name = ?",
                schema, table));
            assertEquals("bigint", columnType(jdbcTemplate, schema, table, "id"));
            assertEquals("varchar(64)", columnType(jdbcTemplate, schema, table, "display_name"));
            assertEquals("decimal(10,2)", columnType(jdbcTemplate, schema, table, "credit_limit"));
            assertEquals("datetime", columnType(jdbcTemplate, schema, table, "registered_at"));
            assertEquals("varchar(255)", columnType(jdbcTemplate, schema, table, "avatar_url"));
            assertEquals("auto_increment", columnExtra(jdbcTemplate, schema, table, "id"));
            assertEquals(1, count(jdbcTemplate,
                "SELECT COUNT(*) FROM information_schema.statistics"
                    + " WHERE table_schema = ? AND table_name = ?"
                    + " AND index_name = 'PRIMARY' AND column_name = 'id'",
                schema, table));
            assertEquals(1, count(jdbcTemplate,
                "SELECT COUNT(*) FROM information_schema.statistics"
                    + " WHERE table_schema = ? AND table_name = ?"
                    + " AND index_name = ? AND column_name = 'display_name'",
                schema, table, "uk_customer_profile_display_name"));

            DdlTableSnapshot snapshot = context.getBean(QueryStrategy.class).readTable(schema, table);
            assertTrue(snapshot.exists());
            assertTrue(snapshot.indexes().stream()
                .anyMatch(index -> "uk_customer_profile_display_name".equals(index.name()) && index.unique()));
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            cleanUp(context, jdbcTemplate, schema, primaryFailure);
        }
    }

    private static EntDdlSpringOptions options(String schema) {
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setEnabled(true);
        options.setSchema(schema);
        options.setCreateDatabaseIfMissing(true);
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        options.setEntityClasses(Collections.<Class<?>>singletonList(
            E5EntityRuntimeStaticAcceptanceTest.CustomerProfile.class));
        return options;
    }

    private static int count(JdbcTemplate jdbcTemplate, String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static String columnType(JdbcTemplate jdbcTemplate, String schema, String table, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT column_type FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
            String.class, schema, table, column);
    }

    private static String columnExtra(JdbcTemplate jdbcTemplate, String schema, String table, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT extra FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
            String.class, schema, table, column);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name, "").trim();
        Assumptions.assumeTrue(!value.isEmpty(), "缺少 MySQL 集成参数: " + name);
        return value;
    }

    private static String mysqlPassword() {
        String password = System.getProperty("entloom.e5.mysql.password", "");
        if (!password.isEmpty()) {
            return password;
        }
        String environmentPassword = System.getenv("ENTLOOM_E5_MYSQL_PASSWORD");
        return environmentPassword == null ? "" : environmentPassword;
    }

    private static void cleanUp(AnnotationConfigApplicationContext context,
                                JdbcTemplate jdbcTemplate,
                                String schema,
                                Throwable primaryFailure) {
        RuntimeException cleanupFailure = null;
        try {
            context.close();
        } catch (RuntimeException exception) {
            cleanupFailure = exception;
        }
        try {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        } catch (RuntimeException exception) {
            if (cleanupFailure == null) {
                cleanupFailure = exception;
            } else {
                cleanupFailure.addSuppressed(exception);
            }
        }
        if (cleanupFailure == null) {
            return;
        }
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw cleanupFailure;
    }
}
