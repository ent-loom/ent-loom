package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.runtime.engine.EngineCapability;
import com.entloom.crud.core.runtime.engine.EngineFeature;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.NoOpQueryDefaultSortResolver;
import com.entloom.crud.core.capability.query.QueryCompiler;
import com.entloom.crud.core.capability.query.QueryDefaultSortResolver;
import com.entloom.crud.core.capability.query.QueryExecutor;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.capability.query.QueryPlanner;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;
import com.entloom.crud.core.util.QueryStrategyResolver;
import com.entloom.crud.enums.QueryStrategy;
import java.util.List;

/**
 * JDBC 默认查询引擎。
 */
public class JdbcQueryEngine implements QueryEngine {
    /** JDBC 默认查询引擎能力声明。 */
    private static final EngineCapability CAPABILITY = EngineCapability.builder("jdbc-query-engine")
        .operations(
            QueryOperation.PAGE,
            QueryOperation.LIST,
            QueryOperation.FIND_ONE,
            QueryOperation.DETAIL
        )
        .queryStrategies(QueryStrategy.ROOT_FIRST)
        .features(
            EngineFeature.ROOT_FILTER,
            EngineFeature.ROOT_SORT,
            EngineFeature.SELECT_FIELDS,
            EngineFeature.RELATION_EXPAND,
            EngineFeature.GOVERNANCE_SCOPE,
            EngineFeature.LOGIC_DELETE,
            EngineFeature.DEFAULT_SORT
        )
        .build();

    /** 实体元数据注册表。 */
    private final EntityMetaRegistry metaRegistry;
    /** 查询规划器。 */
    private final QueryPlanner queryPlanner;
    /** 查询编译器。 */
    private final QueryCompiler queryCompiler;
    /** 查询执行器。 */
    private final QueryExecutor queryExecutor;
    /** SQL 安全守卫。 */
    private final SqlSecurityGuard sqlSecurityGuard;
    /** 默认排序解析器。 */
    private final QueryDefaultSortResolver defaultSortResolver;

    public JdbcQueryEngine(
        EntityMetaRegistry metaRegistry,
        QueryPlanner queryPlanner,
        QueryCompiler queryCompiler,
        QueryExecutor queryExecutor,
        SqlSecurityGuard sqlSecurityGuard
    ) {
        this(
            metaRegistry,
            queryPlanner,
            queryCompiler,
            queryExecutor,
            sqlSecurityGuard,
            NoOpQueryDefaultSortResolver.INSTANCE
        );
    }

    public JdbcQueryEngine(
        EntityMetaRegistry metaRegistry,
        QueryPlanner queryPlanner,
        QueryCompiler queryCompiler,
        QueryExecutor queryExecutor,
        SqlSecurityGuard sqlSecurityGuard,
        QueryDefaultSortResolver defaultSortResolver
    ) {
        this.metaRegistry = metaRegistry;
        this.queryPlanner = queryPlanner;
        this.queryCompiler = queryCompiler;
        this.queryExecutor = queryExecutor;
        this.sqlSecurityGuard = sqlSecurityGuard;
        this.defaultSortResolver = defaultSortResolver == null ? NoOpQueryDefaultSortResolver.INSTANCE : defaultSortResolver;
    }

    @Override
    public EngineCapability capability() {
        return CAPABILITY;
    }

    @Override
    public <R> PageResult<R> page(QuerySpec<R> spec) {
        QueryExecutionSpec<R> workingSpec = prepareWorkingSpec(spec, QueryOperation.PAGE);
        CompiledQuery query = compile(workingSpec);
        return queryExecutor.executePage(query, workingSpec.getResultType());
    }

    @Override
    public <R> List<R> list(QuerySpec<R> spec) {
        QueryExecutionSpec<R> workingSpec = prepareWorkingSpec(spec, QueryOperation.LIST);
        CompiledQuery query = compile(workingSpec);
        return queryExecutor.executeList(query, workingSpec.getResultType());
    }

    @Override
    public <R> R findOne(QuerySpec<R> spec) {
        QueryExecutionSpec<R> workingSpec = prepareWorkingSpec(spec, QueryOperation.FIND_ONE);
        CompiledQuery query = compile(workingSpec);
        return queryExecutor.executeFindOne(query, workingSpec.getResultType());
    }

    @Override
    public <R> R detail(QuerySpec<R> spec) {
        QueryExecutionSpec<R> workingSpec = prepareWorkingSpec(spec, QueryOperation.DETAIL);
        CompiledQuery query = compile(workingSpec);
        return queryExecutor.executeOne(query, workingSpec.getResultType());
    }

    private <R> QueryExecutionSpec<R> prepareWorkingSpec(QuerySpec<R> spec, QueryOperation op) {
        QuerySpec<R> snapshot = SpecSnapshotFactory.copy(spec);
        QueryExecutionSpec<R> workingSpec = QueryExecutionSpec.<R>executionBuilder().from(snapshot).op(op).build();
        validateCapability(workingSpec);
        return workingSpec;
    }

    private void validateCapability(QueryExecutionSpec<?> spec) {
        EngineCapability capability = capability();
        capability.requireOperation(spec.getOperationKey());
        QueryStrategy effective = QueryStrategyResolver.resolveEffectiveStrategy(
            spec.getHandlerDefaultStrategy(),
            spec.getStrategy(),
            QueryStrategy.ROOT_FIRST
        );
        capability.requireQueryStrategy(effective);
        if (!spec.getSelectFields().isEmpty()) {
            capability.requireFeature(EngineFeature.SELECT_FIELDS, "显式字段投影(selectFields)");
        }
        for (QueryFilter filter : spec.getFilters()) {
            if (filter != null && filter.getField() != null && filter.getField().contains(".")) {
                capability.requireFeature(EngineFeature.RELATION_FILTER, "关联过滤");
            }
        }
        for (QuerySort sort : spec.getSorts()) {
            if (sort != null && sort.getField() != null && sort.getField().contains(".")) {
                capability.requireFeature(EngineFeature.RELATION_SORT, "关联排序");
            }
        }
        if (!spec.getExpandRelations().isEmpty()) {
            capability.requireFeature(EngineFeature.RELATION_EXPAND, "关联展开(expandRelations)");
        }
    }

    private <R> CompiledQuery compile(QueryExecutionSpec<R> spec) {
        EntityMeta entityMeta = metaRegistry.getEntityMeta(spec.getRootType());
        RelationGraph relationGraph = metaRegistry.getRelationGraph(spec.getRootType());
        QueryExecutionSpec<R> effectiveSpec = applyDefaultSort(spec, entityMeta);
        QueryPlan queryPlan = queryPlanner.plan(effectiveSpec, entityMeta, relationGraph);
        sqlSecurityGuard.beforeCompile(effectiveSpec);
        return queryCompiler.compile(queryPlan);
    }

    private <R> QueryExecutionSpec<R> applyDefaultSort(QueryExecutionSpec<R> spec, EntityMeta entityMeta) {
        List<QuerySort> defaultSorts = defaultSortResolver.resolve(spec, entityMeta);
        if (defaultSorts == null || defaultSorts.isEmpty()) {
            return spec;
        }
        return QueryExecutionSpec.<R>executionBuilder().from(spec).sorts(defaultSorts).build();
    }
}
