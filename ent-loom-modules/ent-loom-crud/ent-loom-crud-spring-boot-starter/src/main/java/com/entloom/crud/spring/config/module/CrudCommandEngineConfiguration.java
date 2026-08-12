package com.entloom.crud.spring.config.module;

import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.command.CrudCommandRegistry;
import com.entloom.crud.engine.jdbc.command.JdbcCrudCommandOptions;
import com.entloom.crud.engine.jdbc.command.JdbcCrudCommandHandler;
import com.entloom.crud.engine.jdbc.command.RegistryBackedCommandEngine;
import com.entloom.crud.spring.config.CrudProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Command 引擎装配。
 */
@Configuration
@ConditionalOnProperty(name = "entloom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
public class CrudCommandEngineConfiguration {
    /**
     * 默认命令处理注册表，自动注入 JDBC 默认处理器。
     */
    @Bean
    @ConditionalOnBean(GuardedSqlExecutor.class)
    public CrudCommandRegistry defaultCrudCommandRegistry(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        CrudProperties properties
    ) {
        JdbcCrudCommandOptions options = new JdbcCrudCommandOptions();
        options.setIgnoreUnchangedNonWritableUpdateFields(
            properties.getCommand().isIgnoreUnchangedNonWritableUpdateFields()
        );
        options.setIgnoreNonWritableUpdateFields(properties.getCommand().isIgnoreNonWritableUpdateFields());
        options.setCreateScopeFieldValidationMode(properties.getCommand().getCreateScopeFieldValidationMode());
        options.setStrictCreateScopeFieldResources(properties.getCommand().getStrictCreateScopeFieldResources());
        CrudCommandRegistry registry = new CrudCommandRegistry();
        registry.setDefaultHandler(new JdbcCrudCommandHandler<>(metaRegistry, guardedSqlExecutor, options));
        return registry;
    }

    /**
     * 基于路由的默认命令引擎，负责根据注册表分发命令处理器。
     */
    @Bean
    @ConditionalOnBean(CrudCommandRegistry.class)
    @ConditionalOnMissingBean(CommandEngine.class)
    public CommandEngine jdbcDefaultCommandEngine(CrudCommandRegistry registry, SqlSecurityGuard sqlSecurityGuard) {
        return new RegistryBackedCommandEngine(registry, sqlSecurityGuard);
    }
}
