package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.enums.QueryStrategy;
import com.entloom.crud.core.exception.UnsupportedQueryStrategyException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.capability.query.QueryPlanner;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.util.QueryStrategyResolver;
import com.entloom.crud.core.foundation.read.relation.RelationQueryCoordinator;
import com.entloom.crud.core.foundation.read.relation.RelationQueryModel;
import com.entloom.crud.core.foundation.read.relation.RelationLoaderRegistry;
import com.entloom.crud.core.foundation.read.relation.RelationQueryPolicy;
import com.entloom.crud.core.foundation.read.relation.ExistsRelationFilterResolver;
import com.entloom.crud.core.foundation.read.relation.ResolvedExistsRelationFilter;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;

/**
 * ROOT_FIRST 查询计划器。
 */
public class RootFirstQueryPlanner implements QueryPlanner {
    private final RelationQueryCoordinator relationQueryCoordinator;
    /** EXISTS 关联过滤解析器。 */
    private final ExistsRelationFilterResolver existsRelationFilterResolver;
    /** 是否具备 EXISTS 所需的实体元数据注册表。 */
    private final boolean supportsExists;

    public RootFirstQueryPlanner() {
        this(null, null);
    }

    public RootFirstQueryPlanner(EntityMetaRegistry metaRegistry) {
        this(metaRegistry, null);
    }

    public RootFirstQueryPlanner(
        EntityMetaRegistry metaRegistry,
        RelationQueryPolicy relationQueryPolicy,
        RelationLoaderRegistry relationLoaderRegistry
    ) {
        this(metaRegistry, new RelationQueryCoordinator(metaRegistry, relationQueryPolicy, relationLoaderRegistry));
    }

    RootFirstQueryPlanner(EntityMetaRegistry metaRegistry, RelationQueryCoordinator relationQueryCoordinator) {
        this.relationQueryCoordinator = relationQueryCoordinator == null
            ? new RelationQueryCoordinator(metaRegistry)
            : relationQueryCoordinator;
        this.existsRelationFilterResolver = new ExistsRelationFilterResolver(metaRegistry);
        this.supportsExists = metaRegistry != null;
    }

    /**
     * 是否支持需要目标实体元数据的 EXISTS 关联过滤。
     */
    public boolean supportsExists() {
        return supportsExists;
    }

    /**
     * 规划根实体优先的查询执行计划。
     */
    @Override
    public QueryPlan plan(QuerySpec<?> spec, EntityMeta entityMeta, RelationGraph relationGraph) {
        QueryStrategy effective = QueryStrategyResolver.resolveEffectiveStrategy(
            spec.getHandlerDefaultStrategy(),
            spec.getStrategy(),
            QueryStrategy.ROOT_FIRST
        );
        if (effective == QueryStrategy.ROOT_FIRST) {
            if (spec.getExistsRelationFilter() != null) {
                throw new ValidationException("EXISTS 关联过滤必须显式使用 QueryStrategy.EXISTS");
            }
            RelationQueryModel relationQueryModel = relationQueryCoordinator.resolve(spec, relationGraph);
            return new QueryPlan(
                spec,
                entityMeta,
                relationGraph,
                effective,
                spec.getOp(),
                spec.getGovernanceScope(),
                spec.getFilters(),
                relationQueryModel.getExpandEdges()
            );
        }
        if (effective != QueryStrategy.EXISTS) {
            throw new UnsupportedQueryStrategyException("默认 JDBC 查询仅支持 ROOT_FIRST 或 EXISTS 策略");
        }
        if (!spec.getExpandRelations().isEmpty() || hasRelationEntityScope(spec)) {
            throw new ValidationException("EXISTS 查询不支持关联展开");
        }
        ResolvedExistsRelationFilter existsRelationFilter = existsRelationFilterResolver.resolve(
            entityMeta,
            relationGraph,
            spec.getExistsRelationFilter()
        );
        return new QueryPlan(
            spec,
            entityMeta,
            relationGraph,
            effective,
            spec.getOp(),
            spec.getGovernanceScope(),
            spec.getFilters(),
            new ArrayList<com.entloom.crud.core.runtime.meta.RelationEdge>(),
            existsRelationFilter
        );
    }

    private boolean hasRelationEntityScope(QuerySpec<?> spec) {
        return spec.getEntityClasses() != null && spec.getEntityClasses().size() > 1;
    }
}
