package com.entloom.ddl.mysql;

import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.mysql.fixture.MysqlAccount;
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
 * MySQL 8 定向集成测试。
 *
 * <p>默认不参与普通测试；通过 {@code mysql-integration} profile 指向隔离的
 * MySQL 8 实例后，验证 Starter、Spring JDBC 和实际建库建表结果。</p>
 */
class DdlMysqlIntegrationTest {

    @Test
    @DisplayName("MySQL 8 中完成建库、建表并保留字段主键索引")
    void shouldCreateMysqlSchemaTableAndIndexes() {
        Assumptions.assumeTrue(Boolean.parseBoolean(
                System.getProperty("entloom.ddl.mysql.integration", "false")),
                "未启用 mysql-integration profile");

        String url = requiredProperty("entloom.ddl.mysql.url");
        String username = requiredProperty("entloom.ddl.mysql.username");
        String password = System.getProperty("entloom.ddl.mysql.password", "");
        String schema = "entloom_ddl_e25_" + UUID.randomUUID().toString().replace("-", "");
        String table = "mysql_account";

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
        try {
            context.refresh();

            assertEquals(1, count(jdbcTemplate,
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE table_schema = ? AND table_name = ?",
                    schema, table));
            assertEquals("bigint", columnType(jdbcTemplate, schema, table, "id"));
            assertEquals("varchar(80)", columnType(jdbcTemplate, schema, table, "display_name"));
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
                    schema, table, "idx_mysql_account_display_name"));

            DdlTableSnapshot snapshot = context.getBean(QueryStrategy.class).readTable(schema, table);
            assertTrue(snapshot.indexes().stream()
                    .anyMatch(index -> "idx_mysql_account_lower_name".equals(index.name())
                            && index.expression().contains("display_name")));
        } finally {
            context.close();
            jdbcTemplate.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    private static EntDdlSpringOptions options(String schema) {
        EntDdlSpringOptions options = new EntDdlSpringOptions();
        options.setEnabled(true);
        options.setSchema(schema);
        options.setCreateDatabaseIfMissing(true);
        options.setMode(DdlExecutionMode.CREATE_TABLE);
        options.setBasePackages(Collections.singletonList("com.entloom.ddl.mysql.fixture"));
        return options;
    }

    private static int count(JdbcTemplate jdbcTemplate, String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private static String columnType(JdbcTemplate jdbcTemplate,
                                     String schema,
                                     String table,
                                     String column) {
        return jdbcTemplate.queryForObject(
                "SELECT column_type FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                String.class, schema, table, column);
    }

    private static String columnExtra(JdbcTemplate jdbcTemplate,
                                      String schema,
                                      String table,
                                      String column) {
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

}
