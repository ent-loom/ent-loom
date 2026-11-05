package com.entloom.crud.core.runtime.meta;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.meta.enums.RelationCardinality;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 授权策略复用已冻结关系图；仅选择本地、指向目标主键的单值标量引用，不推导权限。 */
public final class EntityRelationResolver {
    private EntityRelationResolver() { }

    public static RelationEdge requireReference(EntityMetaRegistry entities, Class<?> rootType,
        Class<?> targetType, String relation) {
        Objects.requireNonNull(entities, "实体注册表不能为空");
        EntityMeta root = entities.getEntityMeta(rootType);
        if (root == null) throw new ValidationException("关系来源实体未注册: " + rootType.getName());
        Map<String, RelationEdge> matches = new LinkedHashMap<>();
        RelationGraph graph = entities.getRelationGraph(rootType);
        if (graph != null) for (RelationEdge edge : graph.outgoingOf(rootType)) {
            if (edge.getScope() != com.entloom.crud.enums.RelationScope.LOCAL_DB) continue;
            if (targetType != null && !targetType.equals(edge.getToEntity())) continue;
            if (relation != null && !relation.equals(edge.getRelationField()) && !relation.equals(edge.getFromField())) continue;
            if (edge.getCardinality() != RelationCardinality.MANY_TO_ONE
                && edge.getCardinality() != RelationCardinality.ONE_TO_ONE) continue;
            EntityMeta target = entities.getEntityMeta(edge.getToEntity());
            EntityFieldMeta field = root.resolveFieldMeta(edge.getFromField());
            EntityFieldMeta targetField = target == null ? null : target.resolveFieldMeta(edge.getToField());
            if (field == null || field.isRelation() || targetField == null || targetField.isRelation()
                || !edge.getToField().equals(target.getIdField())) continue;
            if (!wrap(field.getJavaType()).equals(wrap(targetField.getJavaType()))) {
                throw new ValidationException("关系引用字段类型不一致: " + rootType.getName() + "#" + edge.getFromField());
            }
            // 同一外键的 ID 声明和导航声明只算一条结构关系。
            matches.putIfAbsent(edge.getFromField() + "->" + edge.getToEntity().getName() + "#" + edge.getToField(), edge);
        }
        String selection = rootType.getName() + " -> " + (targetType == null ? relation : targetType.getName())
            + (targetType != null && relation != null ? "#" + relation : "");
        if (matches.isEmpty()) throw new ValidationException("没有可用的单值主键引用关系: " + selection);
        if (matches.size() != 1) throw new ValidationException("关系不唯一，请显式选择关系属性: " + selection);
        return RelationEdge.immutableCopyOf(matches.values().iterator().next());
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }
}
