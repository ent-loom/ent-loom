package com.entloom.ddl.api;

/**
 * 字段元数据。
 */
public final class DdlFieldMetadata {
    private final String fieldName;
    private final String columnName;
    private final Class<?> javaType;
    private final String columnDefinition;
    private final boolean nullable;
    private final boolean unique;
    private final boolean persisted;
    private final boolean primaryKey;
    private final int length;
    private final int precision;
    private final int scale;
    private final String defaultValue;
    private final String comment;
    private final String renameFrom;

    public DdlFieldMetadata(String fieldName,
                            String columnName,
                            Class<?> javaType,
                            String columnDefinition,
                            boolean nullable,
                            boolean unique,
                            boolean persisted,
                            boolean primaryKey,
                            int length,
                            int precision,
                            int scale,
                            String defaultValue,
                            String comment,
                            String renameFrom) {
        this.fieldName = requireText(fieldName, "fieldName");
        this.columnName = requireText(columnName, "columnName");
        this.javaType = javaType == null ? Object.class : javaType;
        this.columnDefinition = trim(columnDefinition);
        this.nullable = nullable;
        this.unique = unique;
        this.persisted = persisted;
        this.primaryKey = primaryKey;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.defaultValue = trim(defaultValue);
        this.comment = trim(comment);
        this.renameFrom = trim(renameFrom);
    }

    public String fieldName() {
        return fieldName;
    }

    public String columnName() {
        return columnName;
    }

    public Class<?> javaType() {
        return javaType;
    }

    public String columnDefinition() {
        return columnDefinition;
    }

    public boolean nullable() {
        return nullable;
    }

    public boolean unique() {
        return unique;
    }

    public boolean persisted() {
        return persisted;
    }

    public boolean primaryKey() {
        return primaryKey;
    }

    public int length() {
        return length;
    }

    public int precision() {
        return precision;
    }

    public int scale() {
        return scale;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public String comment() {
        return comment;
    }

    public String renameFrom() {
        return renameFrom;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
