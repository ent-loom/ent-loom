package com.entloom.ddl.starter;

import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import com.entloom.ddl.spring.SpringJdbcQueryStrategy;
import com.entloom.ddl.spring.SpringJdbcSqlExecutor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DDL Starter 默认 SPI 装配合同测试。
 */
class EntDdlAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EntDdlAutoConfiguration.class);

    @Test
    @DisplayName("Starter 不默认注册 Noop 查询和执行器")
    void shouldNotRegisterNoopSpiByDefault() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(EntDdlAutoConfiguration.class);
        context.refresh();
        try {
            assertTrue(context.getBeansOfType(QueryStrategy.class).isEmpty());
            assertTrue(context.getBeansOfType(SqlExecutor.class).isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("Starter 提供 Boot 3 自动配置导入声明")
    void shouldDeclareBootAutoConfigurationImport() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertTrue(input != null);
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains(EntDdlAutoConfiguration.class.getName()));
        }
    }

    @Test
    @DisplayName("关闭配置时 Starter 上下文正常启动且不需要 SPI")
    void shouldStartWhenDisabledWithoutSpi() {
        contextRunner
                .withPropertyValues("entloom.ddl.enabled=false", "entloom.ddl.mode=CREATE_TABLE")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    assertTrue(context.getBeansOfType(QueryStrategy.class).isEmpty());
                    assertTrue(context.getBeansOfType(SqlExecutor.class).isEmpty());
                });
    }

    @Test
    @DisplayName("启用配置但缺少 SPI 时提供明确诊断")
    void shouldDiagnoseMissingSpiWhenEnabled() {
        contextRunner
                .withPropertyValues("entloom.ddl.enabled=true", "entloom.ddl.mode=CREATE_TABLE")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    assertTrue(context.getStartupFailure().toString()
                            .contains("DDL 已启用但未配置 QueryStrategy 或 SqlExecutor"));
                });
    }

    @Test
    @DisplayName("存在 DataSource 时自动装配 Spring JDBC SPI")
    void shouldAssembleJdbcSpiWhenDataSourceIsPresent() {
        contextRunner
                .withBean(DataSource.class, EntDdlAutoConfigurationTest::dataSource)
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    assertTrue(context.getBean(QueryStrategy.class) instanceof SpringJdbcQueryStrategy);
                    assertTrue(context.getBean(SqlExecutor.class) instanceof SpringJdbcSqlExecutor);
                });
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ddl_starter_contract;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
