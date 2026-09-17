package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面向实体字段的不可变行约束 AST。
 *
 * <p>该类型只描述字段和值，不携带主体、权限或 SQL 片段。值和子节点均在构造时复制，
 * 组合操作返回新对象，不修改已有约束。</p>
 */
public final class RowConstraint {
    /** 约束节点类型。 */
    public enum Kind {
        /** 无限制。 */
        UNRESTRICTED("无限制"),
        /** 字段比较。 */
        PREDICATE("字段条件"),
        /** 与。 */
        AND("并且"),
        /** 或。 */
        OR("或者"),
        /** 非。 */
        NOT("取反");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final RowConstraint UNRESTRICTED = new RowConstraint(
        Kind.UNRESTRICTED, null, null, Collections.emptyList(), Collections.emptyList()
    );

    private final Kind kind;
    private final String field;
    private final RowConstraintOperator operator;
    private final List<Object> values;
    private final List<RowConstraint> children;

    private RowConstraint(
        Kind kind,
        String field,
        RowConstraintOperator operator,
        List<Object> values,
        List<RowConstraint> children
    ) {
        this.kind = kind;
        this.field = field;
        this.operator = operator;
        this.values = Collections.unmodifiableList(new ArrayList<Object>(values));
        this.children = Collections.unmodifiableList(new ArrayList<RowConstraint>(children));
    }

    /** 创建显式无限制条件。 */
    public static RowConstraint unrestricted() {
        return UNRESTRICTED;
    }

    /** 创建等值条件；值为 null 时保留为 SQL NULL 语义，由编译器或校验器决定是否允许。 */
    public static RowConstraint eq(String field, Object value) {
        return predicate(field, RowConstraintOperator.EQ, value);
    }

    /** 创建集合包含条件。 */
    public static RowConstraint in(String field, Collection<?> values) {
        if (values == null) {
            throw new ValidationException("IN 条件值集合不能为空");
        }
        return new RowConstraint(
            Kind.PREDICATE,
            requireField(field),
            RowConstraintOperator.IN,
            copyValues(values),
            Collections.<RowConstraint>emptyList()
        );
    }

    /** 创建空值条件。 */
    public static RowConstraint isNull(String field) {
        return new RowConstraint(
            Kind.PREDICATE,
            requireField(field),
            RowConstraintOperator.IS_NULL,
            Collections.<Object>emptyList(),
            Collections.<RowConstraint>emptyList()
        );
    }

    /** 创建非空值条件。 */
    public static RowConstraint isNotNull(String field) {
        return new RowConstraint(
            Kind.PREDICATE,
            requireField(field),
            RowConstraintOperator.IS_NOT_NULL,
            Collections.<Object>emptyList(),
            Collections.<RowConstraint>emptyList()
        );
    }

    /** 创建任意比较条件，供后续查询编译能力使用。 */
    public static RowConstraint predicate(String field, RowConstraintOperator operator, Object value) {
        if (operator == null || operator == RowConstraintOperator.IN
            || operator == RowConstraintOperator.IS_NULL || operator == RowConstraintOperator.IS_NOT_NULL) {
            throw new ValidationException("predicate 不支持该操作符: " + operator);
        }
        List<Object> values = new ArrayList<Object>();
        values.add(copyValue(value));
        return new RowConstraint(
            Kind.PREDICATE,
            requireField(field),
            operator,
            values,
            Collections.<RowConstraint>emptyList()
        );
    }

    /** 创建并条件。 */
    public static RowConstraint and(RowConstraint... constraints) {
        if (constraints == null || constraints.length == 0) {
            return unrestricted();
        }
        return combine(Kind.AND, Arrays.asList(constraints));
    }

    /** 创建或条件。 */
    public static RowConstraint or(RowConstraint... constraints) {
        if (constraints == null || constraints.length == 0) {
            throw new ValidationException("OR 条件不能为空");
        }
        return combine(Kind.OR, Arrays.asList(constraints));
    }

    /** 创建非条件。 */
    public static RowConstraint not(RowConstraint constraint) {
        if (constraint == null) {
            throw new ValidationException("NOT 条件不能为空");
        }
        return new RowConstraint(
            Kind.NOT,
            null,
            null,
            Collections.<Object>emptyList(),
            Collections.singletonList(constraint)
        );
    }

    private static RowConstraint combine(Kind kind, Collection<RowConstraint> source) {
        List<RowConstraint> children = new ArrayList<RowConstraint>();
        for (RowConstraint constraint : source) {
            if (constraint == null) {
                throw new ValidationException("行约束子节点不能为空");
            }
            if (constraint.kind == kind) {
                children.addAll(constraint.children);
            } else {
                children.add(constraint);
            }
        }
        if (children.isEmpty()) {
            return unrestricted();
        }
        if (children.size() == 1 && kind == Kind.AND) {
            return children.get(0);
        }
        return new RowConstraint(
            kind,
            null,
            null,
            Collections.<Object>emptyList(),
            children
        );
    }

    private static String requireField(String field) {
        if (field == null || field.trim().isEmpty()) {
            throw new ValidationException("行约束字段不能为空");
        }
        return field.trim();
    }

    private static List<Object> copyValues(Collection<?> values) {
        List<Object> copy = new ArrayList<Object>();
        for (Object value : values) {
            copy.add(copyValue(value));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?>) {
            return Collections.unmodifiableList(copyValues((Collection<?>) value));
        }
        if (value instanceof Map<?, ?>) {
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(copyValue(entry.getKey()), copyValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                copy.add(copyValue(Array.get(value, i)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    public Kind getKind() {
        return kind;
    }

    public String getField() {
        return field;
    }

    public RowConstraintOperator getOperator() {
        return operator;
    }

    public List<Object> getValues() {
        return values;
    }

    public List<RowConstraint> getChildren() {
        return children;
    }

    public boolean isUnrestricted() {
        return kind == Kind.UNRESTRICTED;
    }
}
