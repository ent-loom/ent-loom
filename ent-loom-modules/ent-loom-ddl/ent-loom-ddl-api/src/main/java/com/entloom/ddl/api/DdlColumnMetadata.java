package com.entloom.ddl.api;

import java.util.Objects;

/**
 * 数据库当前列的结构快照。
 *
 * <p>该模型只描述数据库侧事实，不携带实体字段名或 Java 类型。SQL 类型由
 * 数据库方言读取策略提供，例如 {@code bigint}、{@code varchar(80)}。</p>
 */
public final class DdlColumnMetadata {
    private final String columnName;
    private final String sqlType;
    private final boolean nullable;
    private final String defaultValue;
    private final boolean hasDefaultValue;
    private final String comment;
    private final boolean autoIncrement;
    private final boolean generated;

    public DdlColumnMetadata(String columnName,
                             String sqlType,
                             boolean nullable,
                             String defaultValue,
                             String comment) {
        this(columnName, sqlType, nullable, defaultValue, comment, false);
    }

    public DdlColumnMetadata(String columnName,
                             String sqlType,
                             boolean nullable,
                             String defaultValue,
                             String comment,
                             boolean autoIncrement) {
        this(columnName, sqlType, nullable, defaultValue, comment, autoIncrement, false);
    }

    /**
     * @param defaultValue 数据库默认值；null 表示无默认值，空字符串表示空字符串默认值
     * @param generated 是否为数据库生成列
     */
    public DdlColumnMetadata(String columnName,
                             String sqlType,
                             boolean nullable,
                             String defaultValue,
                             String comment,
                             boolean autoIncrement,
                             boolean generated) {
        this.columnName = requireText(columnName, "columnName");
        this.sqlType = requireText(sqlType, "sqlType");
        this.nullable = nullable;
        this.defaultValue = trim(defaultValue);
        this.hasDefaultValue = defaultValue != null;
        this.comment = trim(comment);
        this.autoIncrement = autoIncrement;
        this.generated = generated;
    }

    public String columnName() {
        return columnName;
    }

    public String sqlType() {
        return sqlType;
    }

    public boolean nullable() {
        return nullable;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    public String comment() {
        return comment;
    }

    public boolean autoIncrement() {
        return autoIncrement;
    }

    public boolean generated() {
        return generated;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DdlColumnMetadata)) {
            return false;
        }
        DdlColumnMetadata that = (DdlColumnMetadata) other;
        return nullable == that.nullable
                && autoIncrement == that.autoIncrement
                && generated == that.generated
                && hasDefaultValue == that.hasDefaultValue
                && columnName.equals(that.columnName)
                && sqlType.equals(that.sqlType)
                && defaultValue.equals(that.defaultValue)
                && comment.equals(that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, sqlType, nullable, defaultValue, hasDefaultValue,
                comment, autoIncrement, generated);
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
