package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 在 insert 前落实绑定范围中的可确定字段值。
 */
public final class InsertConstraintValueBinder {
    private InsertConstraintValueBinder() {
    }

    /**
     * 校验并返回带有 DAO 强制填充值的实体字段快照。
     *
     * <p>这里只接受字段等值、非空 IN 和 AND；其它表达式直接 fail-closed。</p>
     */
    public static Map<String, Object> bind(
        RowConstraint constraint,
        EntityMeta meta,
        Map<String, Object> entityValues
    ) {
        if (constraint == null || meta == null || entityValues == null) {
            throw new ValidationException("insert 行约束、元数据和实体值不能为空");
        }
        RowConstraint normalized = RowConstraintNormalizer.normalize(constraint, meta);
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>(entityValues);
        bindNode(normalized, meta, result);
        return Collections.unmodifiableMap(result);
    }

    private static void bindNode(RowConstraint constraint, EntityMeta meta, Map<String, Object> values) {
        switch (constraint.getKind()) {
            case UNRESTRICTED:
                return;
            case AND:
                for (RowConstraint child : constraint.getChildren()) {
                    bindNode(child, meta, values);
                }
                return;
            case PREDICATE:
                bindPredicate(constraint, meta, values);
                return;
            case OR:
            case NOT:
            default:
                throw new ValidationException("insert 不支持的行约束表达式: " + constraint.getKind().getDisplayName());
        }
    }

    private static void bindPredicate(RowConstraint constraint, EntityMeta meta, Map<String, Object> values) {
        String fieldName = constraint.getField();
        EntityFieldMeta field = meta.resolveFieldMeta(fieldName);
        if (field == null || !field.isScopeField()) {
            throw new ValidationException("insert 行约束字段必须是已声明的范围字段: " + fieldName);
        }
        if (constraint.getOperator() == RowConstraintOperator.EQ) {
            if (constraint.getValues().size() != 1 || constraint.getValues().get(0) == null) {
                throw new ValidationException("insert 等值范围值不能为 NULL: " + fieldName);
            }
            putOrVerify(values, fieldName, constraint.getValues().get(0));
            return;
        }
        if (constraint.getOperator() != RowConstraintOperator.IN) {
            throw new ValidationException("insert 不支持的范围操作符: " + constraint.getOperator().getDisplayName());
        }
        if (constraint.getValues().isEmpty()) {
            throw new ValidationException("insert IN 范围集合不能为空: " + fieldName);
        }
        Set<Object> uniqueValues = new LinkedHashSet<Object>();
        for (Object value : constraint.getValues()) {
            if (value == null) {
                throw new ValidationException("insert IN 范围值不能为 NULL: " + fieldName);
            }
            uniqueValues.add(value);
        }
        if (uniqueValues.size() == 1) {
            putOrVerify(values, fieldName, uniqueValues.iterator().next());
            return;
        }
        if (!values.containsKey(fieldName) || values.get(fieldName) == null) {
            throw new ValidationException("insert 多元素 IN 范围必须由实体提供字段值: " + fieldName);
        }
        Object actual = RowConstraintNormalizer.normalizeValue(field, values.get(fieldName));
        if (!uniqueValues.contains(actual)) {
            throw new ValidationException("insert 范围字段值不属于 IN 集合: " + fieldName);
        }
        values.put(fieldName, actual);
    }

    private static void putOrVerify(Map<String, Object> values, String fieldName, Object expected) {
        if (values.containsKey(fieldName) && values.get(fieldName) != null
            && !expected.equals(values.get(fieldName))) {
            throw new ValidationException("insert 范围字段值与绑定范围冲突: " + fieldName);
        }
        values.put(fieldName, expected);
    }
}
