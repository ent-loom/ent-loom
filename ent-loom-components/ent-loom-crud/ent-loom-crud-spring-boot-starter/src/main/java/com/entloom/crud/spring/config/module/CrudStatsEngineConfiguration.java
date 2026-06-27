package com.entloom.crud.spring.config.module;

import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.runtime.scene.DefaultSceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.core.capability.stats.StatsQueryExecutor;
import com.entloom.crud.core.capability.stats.StatsQueryEngine;
import com.entloom.crud.core.capability.stats.DefaultStatsQueryEngine;
import com.entloom.crud.core.capability.stats.StatsGateway;
import com.entloom.crud.core.capability.stats.StatsGatewayImpl;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.stats.StatsSpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stats 查询引擎装配。
 */
@Configuration
public class CrudStatsEngineConfiguration {
    @Bean
    @ConditionalOnBean(GuardedSqlExecutor.class)
    @ConditionalOnMissingBean(name = "defaultStatsQueryExecutor")
    public StatsQueryExecutor defaultStatsQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        SqlSecurityGuard sqlSecurityGuard,
        JdbcDialect jdbcDialect
    ) {
        try {
            Class<?> type = Class.forName("com.entloom.crud.engine.jdbc.stats.query.JdbcStatsQueryExecutor");
            java.lang.reflect.Constructor<?> constructor = type.getConstructor(
                GuardedSqlExecutor.class,
                SqlSecurityGuard.class,
                JdbcDialect.class
            );
            return (StatsQueryExecutor) constructor.newInstance(guardedSqlExecutor, sqlSecurityGuard, jdbcDialect);
        } catch (Exception ex) {
            throw new IllegalStateException("创建 JdbcStatsQueryExecutor 失败", ex);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public SceneHandlerRegistry<StatsSpec, StatsResult> statsSceneHandlerRegistry() {
        return new DefaultSceneHandlerRegistry<StatsSpec, StatsResult>();
    }

    @Bean
    @ConditionalOnMissingBean
    public StatsPayloadCustomizerRegistry statsPayloadCustomizerRegistry() {
        return new StatsPayloadCustomizerRegistry();
    }

    @Bean
    @ConditionalOnBean(name = "defaultStatsQueryExecutor")
    @ConditionalOnMissingBean(StatsQueryEngine.class)
    public StatsQueryEngine statsQueryEngine(
        EntityMetaRegistry metaRegistry,
        @Qualifier("defaultStatsQueryExecutor") StatsQueryExecutor statsQueryExecutor,
        SceneHandlerRegistry<StatsSpec, StatsResult> statsSceneHandlerRegistry
    ) {
        return new DefaultStatsQueryEngine(metaRegistry, statsQueryExecutor, statsSceneHandlerRegistry);
    }

    @Bean
    @ConditionalOnBean(StatsQueryEngine.class)
    @ConditionalOnMissingBean(StatsGateway.class)
    public StatsGateway statsGateway(
        StatsQueryEngine statsQueryEngine,
        ExecutionPipeline executionPipeline
    ) {
        return new StatsGatewayImpl(statsQueryEngine, executionPipeline);
    }
}
