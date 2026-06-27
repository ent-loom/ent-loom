package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 负责决定 options.filter 简写下，字符串值是按 EQ 还是 LIKE 归一化。
 */
public final class CrudStringFilterPolicy {
    /**
     * LIKE 值补全模式。
     */
    public enum LikeMode {
        CONTAINS,
        PREFIX,
        SUFFIX,
        RAW
    }

    private static final CrudStringFilterPolicy DISABLED_POLICY = new CrudStringFilterPolicy(
        false,
        LikeMode.CONTAINS,
        Collections.<String>emptySet()
    );

    private final boolean defaultLikeEnabled;
    private final LikeMode likeMode;
    private final Set<String> likeExcludeFields;

    public CrudStringFilterPolicy(
        boolean defaultLikeEnabled,
        LikeMode likeMode,
        Set<String> likeExcludeFields
    ) {
        this.defaultLikeEnabled = defaultLikeEnabled;
        this.likeMode = likeMode == null ? LikeMode.CONTAINS : likeMode;
        this.likeExcludeFields = normalizeFields(likeExcludeFields);
    }

    public static CrudStringFilterPolicy disabled() {
        return DISABLED_POLICY;
    }

    /**
     * 基于 rootType/field/value 选择 EQ 或 LIKE。
     */
    QueryFilter resolveScalarFilter(Class<?> rootType, String field, Object value) {
        if (shouldUseLike(rootType, field, value)) {
            return new QueryFilter(field, FilterOperator.LIKE, buildLikeValue((String) value));
        }
        return new QueryFilter(field, FilterOperator.EQ, value);
    }

    private boolean shouldUseLike(Class<?> rootType, String field, Object value) {
        if (!defaultLikeEnabled || !(value instanceof String) || rootType == null) {
            return false;
        }
        if (field == null || field.contains(".")) {
            return false;
        }
        if (likeExcludeFields.contains(normalizeFieldName(field))) {
            return false;
        }
        Class<?> fieldType = resolveFieldType(rootType, field);
        if (fieldType == null || fieldType.isEnum()) {
            return false;
        }
        return String.class.isAssignableFrom(fieldType);
    }

    private Object buildLikeValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        switch (likeMode) {
            case PREFIX:
                return rawValue + "%";
            case SUFFIX:
                return "%" + rawValue;
            case RAW:
                return rawValue;
            case CONTAINS:
            default:
                return "%" + rawValue + "%";
        }
    }

    private Class<?> resolveFieldType(Class<?> rootType, String field) {
        Class<?> cursor = rootType;
        while (cursor != null && cursor != Object.class) {
            try {
                Field declaredField = cursor.getDeclaredField(field);
                return declaredField.getType();
            } catch (NoSuchFieldException ex) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private Set<String> normalizeFields(Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<String>();
        for (String field : fields) {
            String name = normalizeFieldName(field);
            if (name != null) {
                normalized.add(name);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private String normalizeFieldName(String field) {
        if (field == null) {
            return null;
        }
        String normalized = field.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
