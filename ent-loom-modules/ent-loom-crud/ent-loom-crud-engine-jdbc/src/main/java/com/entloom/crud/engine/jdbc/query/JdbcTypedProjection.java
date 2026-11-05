package com.entloom.crud.engine.jdbc.query;

import com.entloom.base.util.reflect.EntityProperties;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 类型化结果的字段合同：在 SQL 前拒绝无法完整授权的类型，避免以 null 冒充字段脱敏。 */
final class JdbcTypedProjection {
    private final EntityMetaRegistry entities;
    private final JdbcReflectiveMapper mapper = new JdbcReflectiveMapper();

    JdbcTypedProjection(EntityMetaRegistry entities) {
        this.entities = entities;
    }

    static boolean dynamic(Class<?> type) {
        return type == CrudRecord.class || type == Map.class;
    }

    List<String> fields(Class<?> type, EntityMeta meta) {
        if (type == null || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new PermissionDeniedException("类型化查询结果必须是具体实体或 DTO");
        }
        List<String> names = new ArrayList<>();
        for (Field property : EntityProperties.fields(type)) {
            EntityFieldMeta field = meta.resolveFieldMeta(property.getName());
            if (relation(meta, property.getName())) continue;
            if (field == null) {
                throw new PermissionDeniedException("查询结果属性不是实体字段: " + property.getName());
            }
            if (!field.isRelation()) names.add(property.getName());
        }
        if (names.isEmpty()) throw new PermissionDeniedException("查询结果类型没有普通实体字段");
        return names;
    }

    void validate(Class<?> type, EntityMeta meta, List<String> selected,
                  List<JdbcAuthorizedRelationReader.Projection> relations) {
        if (dynamic(type)) return;
        for (String field : fields(type, meta)) {
            if (!selected.contains(field)) {
                throw new PermissionDeniedException("结果类型要求完整字段授权，请使用较小的 DTO 或动态投影: " + field);
            }
        }
        for (JdbcAuthorizedRelationReader.Projection relation : relations) {
            Field property = property(type, relation.edge.getRelationField());
            if (property == null) {
                throw new PermissionDeniedException("结果类型没有请求展开的关系: " + relation.edge.getRelationField());
            }
            if ((relation.edge.getCardinality() == RelationCardinality.ONE_TO_MANY)
                != Collection.class.isAssignableFrom(property.getType())) {
                throw new PermissionDeniedException("结果属性与关系基数不匹配: " + property.getName());
            }
            Class<?> childType = childType(property);
            // 子结果同样必须完整授权，不能将受限子记录映射为包含更多字段的实体。
            validate(childType, relation.target, relation.returned, Collections.emptyList());
        }
    }

    <R> R map(CrudRecord row, Class<R> type, EntityMeta meta,
              List<JdbcAuthorizedRelationReader.Projection> relations) {
        if (type == CrudRecord.class) return type.cast(row);
        if (type == Map.class) return type.cast(row.asMap());
        Map<String, Object> values = new LinkedHashMap<>(row.asMap());
        for (Field property : EntityProperties.fields(type)) {
            if (!relation(meta, property.getName())) continue;
            JdbcAuthorizedRelationReader.Projection projection = null;
            for (JdbcAuthorizedRelationReader.Projection candidate : relations) {
                if (property.getName().equals(candidate.edge.getRelationField())) projection = candidate;
            }
            // 未展开关系显式置空，避免实体构造器默认对象被当作查询结果返回。
            Object value = null;
            if (projection != null) {
                Class<?> childType = childType(property);
                if (Collection.class.isAssignableFrom(property.getType())) {
                    List<Object> children = new ArrayList<>();
                    for (CrudRecord child : row.records(property.getName())) {
                        children.add(map(child, childType, projection.target, Collections.emptyList()));
                    }
                    value = Set.class.isAssignableFrom(property.getType()) ? new LinkedHashSet<>(children) : children;
                } else {
                    CrudRecord child = row.record(property.getName());
                    value = child == null ? null : map(child, childType, projection.target, Collections.emptyList());
                }
            }
            values.put(property.getName(), value);
        }
        return mapper.mapRow(values, type);
    }

    private boolean relation(EntityMeta meta, String name) {
        EntityFieldMeta field = meta.resolveFieldMeta(name);
        if (field != null) return field.isRelation();
        return entities.getRelationGraph(meta.getEntityType()).outgoingOf(meta.getEntityType()).stream()
            .anyMatch(edge -> name.equals(edge.getRelationField()));
    }

    private static Field property(Class<?> type, String name) {
        for (Field field : EntityProperties.fields(type)) {
            if (name.equals(field.getName())) return field;
        }
        return null;
    }

    private static Class<?> childType(Field property) {
        if (Collection.class.isAssignableFrom(property.getType())
            && !property.getType().isAssignableFrom(ArrayList.class)
            && !property.getType().isAssignableFrom(LinkedHashSet.class)) {
            throw new PermissionDeniedException("类型化集合关系须使用 List、Set 或 Collection: " + property.getName());
        }
        Class<?> type = Collection.class.isAssignableFrom(property.getType())
            ? EntityProperties.describe(property).elementType() : property.getType();
        if (type == null || dynamic(type)) {
            throw new PermissionDeniedException("类型化关系必须声明明确的实体或 DTO 类型: " + property.getName());
        }
        return type;
    }
}
