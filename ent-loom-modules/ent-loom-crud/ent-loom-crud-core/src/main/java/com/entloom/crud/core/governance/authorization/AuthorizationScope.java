package com.entloom.crud.core.governance.authorization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 显式全量、无数据或受限谓词；不使用空 Map 表示全量。 */
public final class AuthorizationScope {
    public enum Kind {
        /** 显式全量。 */
        ALL,
        /** 无可访问数据。 */
        NONE,
        /** 字段等值。 */
        EQUAL,
        /** 字段集合。 */
        IN,
        /** 条件交集。 */
        AND,
        /** 条件并集。 */
        OR
    }

    private static final AuthorizationScope ALL = new AuthorizationScope(Kind.ALL, null,
        Collections.emptyList(), Collections.emptyList());
    private static final AuthorizationScope NONE = new AuthorizationScope(Kind.NONE, null,
        Collections.emptyList(), Collections.emptyList());

    private final Kind kind;
    private final String field;
    private final List<Object> values;
    private final List<AuthorizationScope> children;

    private AuthorizationScope(Kind kind, String field, List<Object> values, List<AuthorizationScope> children) {
        this.kind = kind;
        this.field = field;
        this.values = values;
        this.children = children;
    }

    public static AuthorizationScope all() { return ALL; }
    public static AuthorizationScope none() { return NONE; }

    public static AuthorizationScope equal(String field, Object value) {
        return new AuthorizationScope(Kind.EQUAL, requireField(field),
            Collections.singletonList(scalar(value)), Collections.emptyList());
    }

    public static AuthorizationScope in(String field, Collection<?> values) {
        Objects.requireNonNull(values, "范围取值不能为空");
        LinkedHashSet<Object> copy = new LinkedHashSet<Object>();
        for (Object value : values) copy.add(scalar(value));
        if (copy.isEmpty()) throw new IllegalArgumentException("范围取值不能为空集合");
        return new AuthorizationScope(Kind.IN, requireField(field),
            Collections.unmodifiableList(new ArrayList<Object>(copy)), Collections.emptyList());
    }

    public static AuthorizationScope and(Collection<AuthorizationScope> children) {
        return composite(Kind.AND, children);
    }

    public static AuthorizationScope or(Collection<AuthorizationScope> children) {
        return composite(Kind.OR, children);
    }

    public static AuthorizationScope intersect(AuthorizationScope left, AuthorizationScope right) {
        Objects.requireNonNull(left, "原授权范围不能为空");
        Objects.requireNonNull(right, "收窄范围不能为空");
        if (left == NONE || right == NONE) return NONE;
        if (left == ALL) return right;
        if (right == ALL) return left;
        return and(Arrays.asList(left, right));
    }

    public Kind getKind() { return kind; }
    public String getField() { return field; }
    public List<Object> getValues() { return values; }
    public List<AuthorizationScope> getChildren() { return children; }

    private static AuthorizationScope composite(Kind kind, Collection<AuthorizationScope> children) {
        Objects.requireNonNull(children, "组合范围不能为空");
        List<AuthorizationScope> copy = new ArrayList<AuthorizationScope>();
        for (AuthorizationScope child : children) copy.add(Objects.requireNonNull(child, "范围子条件不能为空"));
        if (copy.isEmpty()) throw new IllegalArgumentException("组合范围至少需要一个条件");
        return new AuthorizationScope(kind, null, Collections.emptyList(), Collections.unmodifiableList(copy));
    }

    private static String requireField(String field) {
        if (field == null || field.trim().isEmpty() || !field.equals(field.trim())) {
            throw new IllegalArgumentException("范围字段不能为空或包含首尾空格");
        }
        return field;
    }

    private static Object scalar(Object value) {
        Objects.requireNonNull(value, "范围取值不能为空");
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        if (value instanceof String || value instanceof Boolean || value instanceof UUID
            || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
            || value instanceof BigInteger || value instanceof BigDecimal) return value;
        if (value instanceof Float && Float.isFinite((Float) value)) return value;
        if (value instanceof Double && Double.isFinite((Double) value)) return value;
        throw new IllegalArgumentException("范围取值必须是不可变标量: " + value.getClass().getName());
    }
}
