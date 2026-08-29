package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已完成元数据绑定的 EXISTS 关联过滤条件。
 */
public final class ResolvedExistsRelationFilter {
    /** 已解析的根实体到目标实体关系边。 */
    private final RelationEdge relationEdge;
    /** 目标实体元数据。 */
    private final EntityMeta targetMeta;
    /** 已校验的目标实体过滤条件。 */
    private final List<QueryFilter> filters;

    public ResolvedExistsRelationFilter(RelationEdge relationEdge, EntityMeta targetMeta, List<QueryFilter> filters) {
        this.relationEdge = relationEdge;
        this.targetMeta = targetMeta;
        this.filters = Collections.unmodifiableList(copyFilters(filters));
    }

    public RelationEdge getRelationEdge() {
        return relationEdge;
    }

    public EntityMeta getTargetMeta() {
        return targetMeta;
    }

    public List<QueryFilter> getFilters() {
        return copyFilters(filters);
    }

    private static List<QueryFilter> copyFilters(List<QueryFilter> source) {
        List<QueryFilter> target = new ArrayList<QueryFilter>();
        if (source == null) {
            return target;
        }
        for (QueryFilter filter : source) {
            if (filter == null) {
                target.add(null);
                continue;
            }
            target.add(new QueryFilter(filter.getField(), filter.getOperator(), filter.getValue()));
        }
        return target;
    }
}
