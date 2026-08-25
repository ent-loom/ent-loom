package com.entloom.ddl.starter;

import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DDL Starter 默认 SPI 装配合同测试。
 */
class EntDdlAutoConfigurationTest {

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
}
