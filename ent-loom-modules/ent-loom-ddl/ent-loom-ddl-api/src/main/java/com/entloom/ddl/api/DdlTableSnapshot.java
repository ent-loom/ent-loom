package com.entloom.ddl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库当前表结构快照。
 *
 * <p>快照由 {@link QueryStrategy} 提供给差异计算使用。索引集合包含普通索引和
 * 唯一约束；主键单独保存以便保留列顺序。不存在的表使用 {@link #missing(String,
 * String)} 表示。</p>
 */
public final class DdlTableSnapshot {
    private final boolean exists;
    private final String schema;
    private final String tableName;
    private final String comment;
    private final List<DdlColumnMetadata> columns;
    private final List<String> primaryKeyColumns;
    private final List<DdlIndexMetadata> indexes;

    public DdlTableSnapshot(boolean exists,
                            String schema,
                            String tableName,
                            String comment,
                            List<DdlColumnMetadata> columns,
                            List<String> primaryKeyColumns,
                            List<DdlIndexMetadata> indexes) {
        this.exists = exists;
        this.schema = trim(schema);
        this.tableName = requireText(tableName, "tableName");
        this.comment = trim(comment);
        this.columns = immutableColumns(columns);
        this.primaryKeyColumns = immutableNames(primaryKeyColumns, "primaryKeyColumns");
        this.indexes = immutableIndexes(indexes);
        validate();
    }

    /**
     * 创建不存在的表快照。
     */
    public static DdlTableSnapshot missing(String schema, String tableName) {
        return new DdlTableSnapshot(false, schema, tableName, "",
                Collections.<DdlColumnMetadata>emptyList(),
                Collections.<String>emptyList(),
                Collections.<DdlIndexMetadata>emptyList());
    }

    public boolean exists() {
        return exists;
    }

    public String schema() {
        return schema;
    }

    public String tableName() {
        return tableName;
    }

    public String comment() {
        return comment;
    }

    public List<DdlColumnMetadata> columns() {
        return columns;
    }

    public List<String> primaryKeyColumns() {
        return primaryKeyColumns;
    }

    public List<DdlIndexMetadata> indexes() {
        return indexes;
    }

    private void validate() {
        Set<String> columnNames = new HashSet<String>();
        for (DdlColumnMetadata column : columns) {
            if (!columnNames.add(column.columnName())) {
                throw new IllegalArgumentException("columns must not contain duplicate: " + column.columnName());
            }
        }
        Set<String> primaryKeys = new HashSet<String>();
        for (String column : primaryKeyColumns) {
            if (!primaryKeys.add(column)) {
                throw new IllegalArgumentException("primaryKeyColumns must not contain duplicate: " + column);
            }
            if (!columnNames.contains(column)) {
                throw new IllegalArgumentException("primary key column does not exist: " + column);
            }
        }
        Set<String> indexNames = new HashSet<String>();
        for (DdlIndexMetadata index : indexes) {
            if (!index.name().isEmpty() && !indexNames.add(index.name())) {
                throw new IllegalArgumentException("index name must not be duplicated: " + index.name());
            }
            if (!index.expression().isEmpty()) {
                continue;
            }
            for (String field : index.fields()) {
                if (!columnNames.contains(field)) {
                    throw new IllegalArgumentException("index column does not exist: " + field);
                }
            }
        }
    }

    private static List<DdlColumnMetadata> immutableColumns(List<DdlColumnMetadata> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<DdlColumnMetadata> copy = new ArrayList<DdlColumnMetadata>(source.size());
        for (DdlColumnMetadata column : source) {
            if (column == null) {
                throw new IllegalArgumentException("columns must not contain null");
            }
            copy.add(column);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<String> immutableNames(List<String> source, String fieldName) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<String>(source.size());
        for (String value : source) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not contain blank");
            }
            copy.add(value.trim());
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<DdlIndexMetadata> immutableIndexes(List<DdlIndexMetadata> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<DdlIndexMetadata> copy = new ArrayList<DdlIndexMetadata>(source.size());
        for (DdlIndexMetadata index : source) {
            if (index == null) {
                throw new IllegalArgumentException("indexes must not contain null");
            }
            copy.add(index);
        }
        return Collections.unmodifiableList(copy);
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
