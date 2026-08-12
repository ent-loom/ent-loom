package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.meta.enums.RelationCardinality;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 关联结果回填助手。
 */
final class JdbcRelationAssignmentHelper {
    private final JdbcReflectionFieldResolver fieldResolver;
    private final boolean relationFieldFallbackEnabled;

    JdbcRelationAssignmentHelper(JdbcReflectionFieldResolver fieldResolver, boolean relationFieldFallbackEnabled) {
        this.fieldResolver = fieldResolver;
        this.relationFieldFallbackEnabled = relationFieldFallbackEnabled;
    }

    /**
     * 将子对象集合写入 root 对象对应关联字段。
     */
    Object assignChildren(Object root, RelationEdge edge, List<Object> children) {
        Class<?> childType = edge == null ? null : edge.getToEntity();
        if (root == null || childType == null) {
            return root;
        }
        if (root instanceof CrudRecord) {
            return assignChildrenToCrudRecord((CrudRecord) root, edge, children);
        }
        if (root instanceof Map<?, ?>) {
            assignChildrenToMap(root, edge, children);
            return root;
        }
        if (edge.getRelationField() != null && !edge.getRelationField().trim().isEmpty()) {
            Optional<Field> explicitField = fieldResolver.resolveField(root.getClass(), edge.getRelationField());
            if (explicitField.isPresent()) {
                writeRelationValue(root, explicitField.get(), children);
                return root;
            }
        }
        if (!relationFieldFallbackEnabled) {
            return root;
        }
        Optional<Field> optionalField = fieldResolver.resolveRelationField(root.getClass(), childType);
        if (!optionalField.isPresent()) {
            return root;
        }
        writeRelationValue(root, optionalField.get(), children);
        return root;
    }

    @SuppressWarnings("unchecked")
    private void assignChildrenToMap(Object root, RelationEdge edge, List<Object> children) {
        Map<String, Object> target = (Map<String, Object>) root;
        String relationField = edge == null || edge.getRelationField() == null || edge.getRelationField().trim().isEmpty()
            ? defaultRelationFieldName(edge)
            : edge.getRelationField();
        List<Object> safeChildren = children == null ? Collections.<Object>emptyList() : children;
        if (edge.getCardinality() == RelationCardinality.ONE_TO_ONE) {
            target.put(relationField, safeChildren.isEmpty() ? null : safeChildren.get(0));
            return;
        }
        target.put(relationField, new ArrayList<Object>(safeChildren));
    }

    private CrudRecord assignChildrenToCrudRecord(CrudRecord root, RelationEdge edge, List<Object> children) {
        String relationField = edge == null || edge.getRelationField() == null || edge.getRelationField().trim().isEmpty()
            ? defaultRelationFieldName(edge)
            : edge.getRelationField();
        if (relationField == null || relationField.trim().isEmpty()) {
            return root;
        }
        LinkedHashMap<String, Object> target = new LinkedHashMap<String, Object>(root.asMap());
        List<Object> safeChildren = children == null ? Collections.<Object>emptyList() : children;
        if (edge.getCardinality() == RelationCardinality.ONE_TO_ONE) {
            target.put(relationField, safeChildren.isEmpty() ? null : safeChildren.get(0));
            return CrudRecord.copyOf(target);
        }
        target.put(relationField, new ArrayList<Object>(safeChildren));
        return CrudRecord.copyOf(target);
    }

    private String resolveRelationFieldName(RelationEdge edge) {
        if (edge == null || edge.getToEntity() == null) {
            return null;
        }
        if (edge.getFromEntity() != null) {
            Optional<Field> fallbackField = fieldResolver.resolveRelationField(edge.getFromEntity(), edge.getToEntity());
            if (fallbackField.isPresent()) {
                return fallbackField.get().getName();
            }
        }
        return edge.getToEntity().getSimpleName();
    }

    private String defaultRelationFieldName(RelationEdge edge) {
        if (relationFieldFallbackEnabled) {
            return resolveRelationFieldName(edge);
        }
        if (edge == null || edge.getToEntity() == null) {
            return null;
        }
        if (edge.getCardinality() == RelationCardinality.ONE_TO_ONE) {
            return lowerCamel(edge.getToEntity().getSimpleName());
        }
        return lowerCamel(edge.getToEntity().getSimpleName()) + "List";
    }

    private String lowerCamel(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private void writeRelationValue(Object root, Field relationField, List<Object> children) {
        try {
            if (Collection.class.isAssignableFrom(relationField.getType())) {
                relationField.set(root, children == null ? Collections.emptyList() : children);
                return;
            }
            List<Object> safeChildren = children == null ? Collections.<Object>emptyList() : children;
            relationField.set(root, safeChildren.isEmpty() ? null : safeChildren.get(0));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法设置关联字段 " + relationField.getName(), e);
        }
    }
}
