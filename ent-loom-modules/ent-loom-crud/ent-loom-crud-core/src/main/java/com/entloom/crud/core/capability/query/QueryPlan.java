package com.entloom.crud.core.capability.query;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.enums.QueryStrategy;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * 查询计划对象。
 */
@Getter
public class QueryPlan {
    /** 规格对象。 */
    private final QuerySpec<?> spec;
    /** 根实体元数据。 */
    private final EntityMeta rootMeta;
    /** 关系图。 */
    private final RelationGraph relationGraph;
    /** 生效的查询策略。 */
    private final QueryStrategy effectiveStrategy;
    /** 操作类型。 */
    private final QueryOperation op;
    /** 治理计算出的数据范围。 */
    private final CrudDataScope governanceScope;
    /** 过滤条件列表。 */
    private final List<QueryFilter> filters;
    /** 解析后的关联展开边。 */
    private final List<com.entloom.crud.core.runtime.meta.RelationEdge> expandEdges;

    public QueryPlan(
        QuerySpec<?> spec,
        EntityMeta rootMeta,
        RelationGraph relationGraph,
        QueryStrategy effectiveStrategy,
        QueryOperation op
    ) {
        this(
            spec,
            rootMeta,
            relationGraph,
            effectiveStrategy,
            op,
            spec.getGovernanceScope(),
            spec.getFilters(),
            new ArrayList<com.entloom.crud.core.runtime.meta.RelationEdge>()
        );
    }

    public QueryPlan(
        QuerySpec<?> spec,
        EntityMeta rootMeta,
        RelationGraph relationGraph,
        QueryStrategy effectiveStrategy,
        QueryOperation op,
        CrudDataScope governanceScope,
        List<QueryFilter> filters
    ) {
        this(
            spec,
            rootMeta,
            relationGraph,
            effectiveStrategy,
            op,
            governanceScope,
            filters,
            new ArrayList<com.entloom.crud.core.runtime.meta.RelationEdge>()
        );
    }

    public QueryPlan(
        QuerySpec<?> spec,
        EntityMeta rootMeta,
        RelationGraph relationGraph,
        QueryStrategy effectiveStrategy,
        QueryOperation op,
        CrudDataScope governanceScope,
        List<QueryFilter> filters,
        List<com.entloom.crud.core.runtime.meta.RelationEdge> expandEdges
    ) {
        this.spec = spec;
        this.rootMeta = rootMeta;
        this.relationGraph = relationGraph;
        this.effectiveStrategy = effectiveStrategy;
        this.op = op;
        this.governanceScope = governanceScope;
        this.filters = filters == null ? new ArrayList<QueryFilter>() : new ArrayList<QueryFilter>(filters);
        this.expandEdges = expandEdges == null
            ? new ArrayList<com.entloom.crud.core.runtime.meta.RelationEdge>()
            : new ArrayList<com.entloom.crud.core.runtime.meta.RelationEdge>(expandEdges);
    }

    public List<QueryFilter> getFilters() {
        return new ArrayList<QueryFilter>(filters);
    }

    public List<com.entloom.crud.core.runtime.meta.RelationEdge> getExpandEdges() {
        return new ArrayList<com.entloom.crud.core.runtime.meta.RelationEdge>(expandEdges);
    }

}
