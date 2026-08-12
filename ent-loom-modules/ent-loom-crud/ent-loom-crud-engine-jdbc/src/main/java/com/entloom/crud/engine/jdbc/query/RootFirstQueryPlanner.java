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

/**
 * ROOT_FIRST 查询计划器。
 */
public class RootFirstQueryPlanner implements QueryPlanner {
    private final RelationQueryCoordinator relationQueryCoordinator;

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
        if (effective != QueryStrategy.ROOT_FIRST) {
            throw new UnsupportedQueryStrategyException("MVP-1 仅支持 ROOT_FIRST 策略");
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
}
