package com.entloom.crud.starter.config.module;

import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

/**
 * 在 Spring 容器刷新完成后校验 JDBC 范围字段数据库结构。
 *
 * <p>使用最低优先级，确保同一容器中的 DDL 监听器先完成建表或修改表结构。</p>
 */
public final class JdbcInsertScopeDatabaseStartupValidator
    implements ApplicationListener<ContextRefreshedEvent>, Ordered {
    private final JdbcInsertScopeDatabaseValidator validator;

    public JdbcInsertScopeDatabaseStartupValidator(JdbcInsertScopeDatabaseValidator validator) {
        if (validator == null) {
            throw new IllegalArgumentException("JdbcInsertScopeDatabaseValidator 不能为空");
        }
        this.validator = validator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        validator.validateOrThrow();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
