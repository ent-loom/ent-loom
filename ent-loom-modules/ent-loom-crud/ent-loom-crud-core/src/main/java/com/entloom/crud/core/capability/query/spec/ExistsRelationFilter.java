package com.entloom.crud.core.capability.query.spec;

import com.entloom.crud.api.model.QueryFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EXISTS 关联过滤条件。
 *
 * <p>仅用于 Java 内部受控查询模型。关系名和字段名都会由运行时元数据解析，禁止传入 SQL 片段。</p>
 */
public final class ExistsRelationFilter {
    /** 根实体上的一跳关系名。 */
    private final String relation;
    /** 目标实体字段的 AND 过滤条件。 */
    private final List<QueryFilter> filters;

    public ExistsRelationFilter(String relation, List<QueryFilter> filters) {
        this.relation = relation;
        this.filters = Collections.unmodifiableList(copyFilters(filters));
    }

    public String getRelation() {
        return relation;
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
