package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 按实体元数据规范化行约束值。
 */
public final class RowConstraintNormalizer {
    private RowConstraintNormalizer() {
    }

    public static RowConstraint normalize(RowConstraint constraint, EntityMeta meta) {
        if (constraint == null || meta == null) {
            throw new ValidationException("行约束和实体元数据不能为空");
        }
        switch (constraint.getKind()) {
            case UNRESTRICTED:
                return RowConstraint.unrestricted();
            case PREDICATE:
                return normalizePredicate(constraint, meta);
            case AND:
                return RowConstraint.and(normalizeChildren(constraint, meta));
            case OR:
                return RowConstraint.or(normalizeChildren(constraint, meta));
            case NOT:
                if (constraint.getChildren().size() != 1) {
                    throw new ValidationException("NOT 条件必须只有一个子节点");
                }
                return RowConstraint.not(normalize(constraint.getChildren().get(0), meta));
            default:
                throw new ValidationException("不支持的行约束类型: " + constraint.getKind());
        }
    }

    private static RowConstraint[] normalizeChildren(RowConstraint constraint, EntityMeta meta) {
        List<RowConstraint> children = constraint.getChildren();
        RowConstraint[] result = new RowConstraint[children.size()];
        for (int i = 0; i < children.size(); i++) {
            result[i] = normalize(children.get(i), meta);
        }
        return result;
    }

    private static RowConstraint normalizePredicate(RowConstraint constraint, EntityMeta meta) {
        String fieldName = constraint.getField();
        EntityFieldMeta field = meta.resolveFieldMeta(fieldName);
        if (field == null || field.isRelation()) {
            throw new ValidationException("行约束字段未注册或为关联字段: " + fieldName);
        }
        switch (constraint.getOperator()) {
            case IN:
                List<Object> normalized = new ArrayList<Object>();
                for (Object value : constraint.getValues()) {
                    normalized.add(normalizeValue(field, value));
                }
                return RowConstraint.in(fieldName, normalized);
            case IS_NULL:
                return RowConstraint.isNull(fieldName);
            case IS_NOT_NULL:
                return RowConstraint.isNotNull(fieldName);
            default:
                if (constraint.getValues().size() != 1) {
                    throw new ValidationException("字段条件值数量无效: " + fieldName);
                }
                return RowConstraint.predicate(
                    fieldName,
                    constraint.getOperator(),
                    normalizeValue(field, constraint.getValues().get(0))
                );
        }
    }

    /** 按字段 Java 类型转换 JDBC 绑定前的值。 */
    public static Object normalizeValue(EntityFieldMeta field, Object value) {
        if (value == null || field == null || field.getJavaType() == null) {
            return value;
        }
        Class<?> target = wrap(field.getJavaType());
        if (target == Object.class || target.isInstance(value)) {
            return value;
        }
        String text = String.valueOf(value);
        try {
            if (target == String.class) {
                return text;
            }
            if (target == Integer.class) {
                return Integer.valueOf(text);
            }
            if (target == Long.class) {
                return Long.valueOf(text);
            }
            if (target == Short.class) {
                return Short.valueOf(text);
            }
            if (target == Byte.class) {
                return Byte.valueOf(text);
            }
            if (target == Double.class) {
                return Double.valueOf(text);
            }
            if (target == Float.class) {
                return Float.valueOf(text);
            }
            if (target == BigDecimal.class) {
                return new BigDecimal(text);
            }
            if (target == BigInteger.class) {
                return new BigInteger(text);
            }
            if (target == LocalDate.class) {
                return LocalDate.parse(text);
            }
            if (target == LocalDateTime.class) {
                return LocalDateTime.parse(text.replace(' ', 'T'));
            }
            if (target == Boolean.class) {
                if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                    return Boolean.FALSE;
                }
            }
            if (target.isEnum()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object result = Enum.valueOf((Class<? extends Enum>) target, text);
                return result;
            }
        } catch (RuntimeException ex) {
            throw new ValidationException("行约束值无法转换为字段类型: " + field.getFieldName() + " = " + value);
        }
        throw new ValidationException("行约束字段类型暂不支持转换: " + field.getFieldName());
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        return type;
    }
}
