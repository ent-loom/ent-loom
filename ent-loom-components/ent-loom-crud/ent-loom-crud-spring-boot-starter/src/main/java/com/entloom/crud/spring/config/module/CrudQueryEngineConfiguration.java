package com.entloom.crud.spring.config.module;

import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.capability.query.ConfigurableQueryDefaultSortResolver;
import com.entloom.crud.core.capability.query.QueryCompiler;
import com.entloom.crud.core.capability.query.QueryDefaultSortResolver;
import com.entloom.crud.core.capability.query.QueryExecutor;
import com.entloom.crud.core.capability.query.QueryPlanner;
import com.entloom.crud.core.foundation.read.relation.RelationLoader;
import com.entloom.crud.core.foundation.read.relation.RelationLoaderRegistry;
import com.entloom.crud.core.foundation.read.relation.RelationQueryPolicy;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.query.JdbcQueryCompiler;
import com.entloom.crud.engine.jdbc.query.JdbcQueryEngine;
import com.entloom.crud.engine.jdbc.query.JdbcQueryExecutor;
import com.entloom.crud.engine.jdbc.query.RootFirstQueryPlanner;
import com.entloom.crud.spring.config.CrudProperties;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Query 引擎装配。
 */
@Configuration
@ConditionalOnProperty(name = "entloom.crud.query.enabled", havingValue = "true", matchIfMissing = true)
public class CrudQueryEngineConfiguration {
    /**
     * 默认排序解析器：无显式排序时为 PAGE/LIST 补齐业务默认排序。
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryDefaultSortResolver queryDefaultSortResolver(CrudProperties properties) {
        CrudProperties.Query.DefaultSort defaultSort = properties.getQuery().getDefaultSort();
        return new ConfigurableQueryDefaultSortResolver(
            defaultSort.isEnabled(),
            defaultSort.getApplyTo(),
            defaultSort.getTimeFields(),
            defaultSort.getTimeDirection(),
            defaultSort.isAppendId(),
            defaultSort.getIdDirection(),
            defaultSort.isFallbackToId()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RelationQueryPolicy relationQueryPolicy(CrudProperties properties) {
        CrudProperties.Relation relation = properties.getRelation();
        return new RelationQueryPolicy(
            relation.getMaxDepth(),
            relation.getMaxExpandEdges(),
            relation.isAllowCycles(),
            relation.isAllowExternalLoaders()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RelationLoaderRegistry relationLoaderRegistry(ObjectProvider<RelationLoader[]> relationLoaders) {
        RelationLoader[] loaders = relationLoaders.getIfAvailable();
        return new RelationLoaderRegistry(loaders == null ? null : Arrays.asList(loaders));
    }

    /**
     * 查询计划器：默认采用 root-first 策略。
     */
    @Bean
    @ConditionalOnBean(GuardedSqlExecutor.class)
    @ConditionalOnMissingBean
    public QueryPlanner queryPlanner(
        EntityMetaRegistry metaRegistry,
        RelationQueryPolicy relationQueryPolicy,
        RelationLoaderRegistry relationLoaderRegistry
    ) {
        return new RootFirstQueryPlanner(metaRegistry, relationQueryPolicy, relationLoaderRegistry);
    }

    /**
     * 查询编译器：将 QuerySpec 编译为可执行 SQL 计划。
     */
    @Bean
    @ConditionalOnBean(GuardedSqlExecutor.class)
    @ConditionalOnMissingBean
    public QueryCompiler queryCompiler(JdbcDialect jdbcDialect) {
        return new JdbcQueryCompiler(jdbcDialect);
    }

    /**
     * 查询执行器：负责执行编译结果并映射实体结果。
     */
    @Bean
    @ConditionalOnBean(GuardedSqlExecutor.class)
    @ConditionalOnMissingBean
    public QueryExecutor queryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        EntityMetaRegistry metaRegistry,
        CrudProperties properties,
        RelationLoaderRegistry relationLoaderRegistry
    ) {
        return new JdbcQueryExecutor(
            guardedSqlExecutor,
            metaRegistry,
            !properties.getRelation().isStrictCollectionFieldName(),
            relationLoaderRegistry
        );
    }

    /**
     * 默认 JDBC QueryEngine。
     */
    @Bean
    @ConditionalOnBean({GuardedSqlExecutor.class, QueryPlanner.class, QueryCompiler.class, QueryExecutor.class})
    @ConditionalOnMissingBean(QueryEngine.class)
    public QueryEngine jdbcQueryEngine(
        EntityMetaRegistry metaRegistry,
        QueryPlanner queryPlanner,
        QueryCompiler queryCompiler,
        QueryExecutor queryExecutor,
        SqlSecurityGuard sqlSecurityGuard,
        QueryDefaultSortResolver queryDefaultSortResolver
    ) {
        return new JdbcQueryEngine(
            metaRegistry,
            queryPlanner,
            queryCompiler,
            queryExecutor,
            sqlSecurityGuard,
            queryDefaultSortResolver
        );
    }
}
