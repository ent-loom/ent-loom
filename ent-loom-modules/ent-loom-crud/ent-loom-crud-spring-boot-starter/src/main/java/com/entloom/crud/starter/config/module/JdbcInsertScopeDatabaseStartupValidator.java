package com.entloom.crud.starter.config.module;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

/**
 * 在 Spring 容器刷新完成后复验 JDBC 范围字段数据库结构。
 *
 * <p>使用最低优先级，确保同一容器中的 DDL 监听器先完成建表或修改表结构。启动期复验失败只记录告警，
 * 不阻塞 Spring 容器；相关范围实体在创建 DAO 时仍由 Factory 严格校验并拒绝使用。</p>
 */
public final class JdbcInsertScopeDatabaseStartupValidator
    implements ApplicationListener<ContextRefreshedEvent>, Ordered {
    private static final Logger log = LoggerFactory.getLogger(JdbcInsertScopeDatabaseStartupValidator.class);

    private final JdbcInsertScopeDatabaseValidator validator;

    public JdbcInsertScopeDatabaseStartupValidator(JdbcInsertScopeDatabaseValidator validator) {
        if (validator == null) {
            throw new IllegalArgumentException("JdbcInsertScopeDatabaseValidator 不能为空");
        }
        this.validator = validator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            validator.validateOrThrow();
        } catch (ValidationException ex) {
            log.warn("JDBC 范围字段数据库结构启动复验失败；主应用继续启动，相关实体 DAO 创建时仍会严格拒绝: {}",
                ex.getMessage(), ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
