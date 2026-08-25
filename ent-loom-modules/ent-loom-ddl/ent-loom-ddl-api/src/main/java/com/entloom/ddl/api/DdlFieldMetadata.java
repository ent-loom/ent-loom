package com.entloom.ddl.api;

import java.util.Objects;

/**
 * DDL 字段元数据。
 *
 * <p>字段名、列名和 Java 类型是必填项。{@code -1} 表示长度、精度或小数位
 * 未显式指定；其他负数属于非法输入。主键和唯一约束只能作用于持久化字段，
 * 主键字段不允许声明为可空。</p>
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
        this.javaType = Objects.requireNonNull(javaType, "javaType must not be null");
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
        validate();
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

    private void validate() {
        if (length < -1) {
            throw new IllegalArgumentException("length must be -1 or greater");
        }
        if (precision < -1) {
            throw new IllegalArgumentException("precision must be -1 or greater");
        }
        if (scale < -1) {
            throw new IllegalArgumentException("scale must be -1 or greater");
        }
        if (!persisted && (unique || primaryKey)) {
            throw new IllegalArgumentException("non-persisted field must not be unique or primary key");
        }
        if (primaryKey && nullable) {
            throw new IllegalArgumentException("primary key field must not be nullable");
        }
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
