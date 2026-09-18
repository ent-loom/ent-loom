package com.entloom.crud.starter.config.module;

import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.JdbcMatchedRowsStartupValidator;
import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL 安全能力装配（白名单校验、参数限制、守卫执行器）。
 */
@Configuration
public class CrudSqlSecurityConfiguration {
    /**
     * SQL 标识符白名单校验器，基于实体元数据约束可访问表/字段。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public SqlIdentifierAllowlistValidator sqlIdentifierAllowlistValidator(EntityMetaRegistry metaRegistry) {
        return new SqlIdentifierAllowlistValidator(metaRegistry);
    }

    /**
     * SQL 参数限制器，拦截参数数量或体量异常的请求。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public SqlParameterLimiter sqlParamLimiter() {
        return new SqlParameterLimiter();
    }

    /**
     * 聚合白名单与参数限制形成统一安全守卫。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public SqlSecurityGuard sqlSecurityGuard(
        SqlIdentifierAllowlistValidator whitelistValidator,
        SqlParameterLimiter sqlParamLimiter
    ) {
        return new SqlSafetyGuard(whitelistValidator, sqlParamLimiter);
    }

    /**
     * 最终执行器：在 JDBC 执行前后接入安全校验与日志记录。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public GuardedSqlExecutor guardedSqlExecutor(
        JdbcTemplate jdbcTemplate,
        SqlSecurityGuard sqlSecurityGuard,
        SqlExecutionLogger sqlExecutionLogger
    ) {
        return new JdbcGuardedSqlExecutor(jdbcTemplate, sqlSecurityGuard, sqlExecutionLogger);
    }

    /**
     * 启动期确认 MySQL 使用 matched-rows 影响行数语义。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public JdbcMatchedRowsStartupValidator jdbcMatchedRowsStartupValidator(DataSource dataSource) {
        JdbcMatchedRowsStartupValidator validator = new JdbcMatchedRowsStartupValidator(dataSource);
        validator.validateOrThrow();
        return validator;
    }

    /**
     * 启动期拒绝会改写 DAO 范围字段的 MySQL 结构。
     */
    @Bean
    @ConditionalOnBean({JdbcTemplate.class, EntityMetaRegistry.class})
    @ConditionalOnMissingBean
    public JdbcInsertScopeDatabaseValidator jdbcInsertScopeDatabaseValidator(
        DataSource dataSource,
        EntityMetaRegistry metaRegistry
    ) {
        return new JdbcInsertScopeDatabaseValidator(dataSource, metaRegistry);
    }

    /**
     * 在 DDL 和其它容器刷新监听器完成后执行范围字段数据库校验。
     */
    @Bean
    @ConditionalOnBean(JdbcInsertScopeDatabaseValidator.class)
    @ConditionalOnMissingBean
    public JdbcInsertScopeDatabaseStartupValidator jdbcInsertScopeDatabaseStartupValidator(
        JdbcInsertScopeDatabaseValidator validator
    ) {
        return new JdbcInsertScopeDatabaseStartupValidator(validator);
    }
}
