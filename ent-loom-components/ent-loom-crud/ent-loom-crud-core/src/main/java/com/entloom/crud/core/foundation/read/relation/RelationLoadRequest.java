package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量关系加载请求。
 */
public final class RelationLoadRequest {
    private final QuerySpec<?> spec;
    private final RelationEdge edge;
    private final List<Object> parents;
    private final List<Object> parentKeys;

    public RelationLoadRequest(
        QuerySpec<?> spec,
        RelationEdge edge,
        List<?> parents,
        List<?> parentKeys
    ) {
        this.spec = spec;
        this.edge = edge;
        this.parents = copy(parents);
        this.parentKeys = copy(parentKeys);
    }

    public QuerySpec<?> getSpec() {
        return spec;
    }

    public RelationEdge getEdge() {
        return edge;
    }

    public List<Object> getParents() {
        return new ArrayList<Object>(parents);
    }

    public List<Object> getParentKeys() {
        return new ArrayList<Object>(parentKeys);
    }

    private static List<Object> copy(List<?> values) {
        List<Object> result = new ArrayList<Object>();
        if (values == null) {
            return result;
        }
        result.addAll(values);
        return result;
    }
}
