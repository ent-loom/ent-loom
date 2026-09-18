package com.entloom.crud.starter.config.module;

import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 范围字段启动复验监听器合同测试。
 */
class JdbcInsertScopeDatabaseStartupValidatorTest {
    @Test
    void should_validate_after_context_refresh_with_lowest_order() {
        JdbcInsertScopeDatabaseValidator validator = mock(JdbcInsertScopeDatabaseValidator.class);
        JdbcInsertScopeDatabaseStartupValidator listener =
            new JdbcInsertScopeDatabaseStartupValidator(validator);

        assertThat(listener.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
        listener.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));

        verify(validator).validateOrThrow();
    }
}
