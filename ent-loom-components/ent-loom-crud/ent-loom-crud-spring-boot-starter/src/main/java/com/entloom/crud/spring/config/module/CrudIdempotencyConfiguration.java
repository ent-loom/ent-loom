package com.entloom.crud.spring.config.module;

import com.entloom.crud.core.idempotency.IdempotencyManager;
import com.entloom.crud.core.idempotency.IdempotencyStore;
import com.entloom.crud.core.idempotency.InMemoryIdempotencyStore;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.idempotency.JdbcIdempotencyStore;
import com.entloom.crud.spring.config.CrudProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 幂等能力装配（存储实现与管理器）。
 */
@Configuration
@ConditionalOnProperty(name = "entloom.crud.command.enabled", havingValue = "true", matchIfMissing = true)
public class CrudIdempotencyConfiguration {
    /**
     * 存在 JdbcTemplate 时优先使用 JDBC 存储，并按配置可选自动建表。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public IdempotencyStore jdbcIdempotencyStore(
        JdbcTemplate jdbcTemplate,
        CrudProperties properties,
        JdbcDialect jdbcDialect
    ) {
        CrudProperties.Idempotency idempotency = properties.getIdempotency();
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(
            jdbcTemplate,
            idempotency.getTableName(),
            Clock.systemUTC(),
            Duration.ofHours(idempotency.getRetentionHours()),
            jdbcDialect
        );
        if (idempotency.isAutoInitializeSchema()) {
            store.initializeSchema();
        }
        return store;
    }

    /**
     * 缺失持久化存储时回退为内存实现，适用于本地或轻量场景。
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore inMemoryIdempotencyStore(CrudProperties properties) {
        return new InMemoryIdempotencyStore(Duration.ofHours(properties.getIdempotency().getRetentionHours()).toMillis());
    }

    /**
     * 幂等管理器统一封装获取/释放语义，对外只暴露策略化接口。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyManager idempotencyManager(IdempotencyStore store) {
        return new IdempotencyManager(store);
    }
}
