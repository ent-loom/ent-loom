package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.query.spec.ExistsRelationFilter;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.enums.RelationScope;
import java.util.List;

/**
 * EXISTS 关联过滤解析器。
 */
public final class ExistsRelationFilterResolver {
    /** 实体元数据注册表。 */
    private final EntityMetaRegistry metaRegistry;
    /** 关系边匹配器。 */
    private final RelationEdgeInferenceResolver edgeResolver;

    public ExistsRelationFilterResolver(EntityMetaRegistry metaRegistry) {
        this.metaRegistry = metaRegistry;
        this.edgeResolver = new RelationEdgeInferenceResolver(metaRegistry);
    }

    /**
     * 解析并校验单个本地一跳 EXISTS 关系过滤。
     */
    public ResolvedExistsRelationFilter resolve(
        EntityMeta rootMeta,
        RelationGraph relationGraph,
        ExistsRelationFilter existsRelationFilter
    ) {
        if (metaRegistry == null) {
            throw new ValidationException("EXISTS 关联过滤需要 EntityMetaRegistry");
        }
        if (existsRelationFilter == null) {
            throw new ValidationException("EXISTS 查询必须提供关联过滤条件");
        }
        String relation = existsRelationFilter.getRelation();
        if (relation == null || relation.trim().isEmpty()) {
            throw new ValidationException("EXISTS 关联关系不能为空");
        }
        List<QueryFilter> filters = existsRelationFilter.getFilters();
        if (filters.isEmpty()) {
            throw new ValidationException("EXISTS 关联过滤条件不能为空");
        }

        RelationEdge edge = edgeResolver.resolveEdge(relationGraph.outgoingOf(rootMeta.getEntityType()), relation.trim());
        if (edge.getScope() != RelationScope.LOCAL_DB) {
            throw new ValidationException("EXISTS 关联过滤仅支持 LOCAL_DB 范围");
        }
        EntityMeta targetMeta = metaRegistry.getEntityMeta(edge.getToEntity());
        if (targetMeta == null) {
            throw new ValidationException("未找到 EXISTS 关联目标实体元数据: " + edge.getToEntity().getName());
        }
        if (rootMeta.resolveColumn(edge.getFromField()) == null || targetMeta.resolveColumn(edge.getToField()) == null) {
            throw new ValidationException("EXISTS 关联字段未在运行时元数据中注册: " + relation);
        }
        validateFilters(filters, targetMeta);
        return new ResolvedExistsRelationFilter(edge, targetMeta, filters);
    }

    private void validateFilters(List<QueryFilter> filters, EntityMeta targetMeta) {
        for (QueryFilter filter : filters) {
            if (filter == null) {
                throw new ValidationException("EXISTS 关联过滤条件不能为空");
            }
            String field = filter.getField();
            if (field == null || field.trim().isEmpty() || field.contains(".")) {
                throw new ValidationException("EXISTS 仅支持目标实体直接字段过滤: " + field);
            }
            if (filter.getOperator() == null) {
                throw new ValidationException("EXISTS 过滤操作符不能为空: " + field);
            }
            EntityFieldMeta fieldMeta = targetMeta.resolveFieldMeta(field);
            if (fieldMeta == null) {
                throw new ValidationException("未知 EXISTS 关联过滤字段: " + field);
            }
            if (!fieldMeta.isFilterable()) {
                throw new ValidationException("EXISTS 关联字段不允许过滤: " + field);
            }
        }
    }
}
