package com.entloom.ddl.api;

import com.entloom.ddl.enums.DdlTableSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 实体到表的 DDL 元数据映射。
 *
 * <p>实体和表名是必填项；字段集合至少要有一个持久化字段。字段和索引
 * 按传入顺序保留，该顺序就是 SQL 生成顺序。重复字段、重复物理列和引用
 * 不存在列的普通索引会在构造时拒绝。</p>
 */
public final class DdlEntityMetadata {
    private final String entityClassName;
    private final String schema;
    private final String tableName;
    private final String comment;
    private final DdlTableSize tableSize;
    private final List<DdlFieldMetadata> fields;
    private final List<DdlIndexMetadata> indexes;

    public DdlEntityMetadata(String entityClassName,
                             String schema,
                             String tableName,
                             String comment,
                             DdlTableSize tableSize,
                             List<DdlFieldMetadata> fields,
                             List<DdlIndexMetadata> indexes) {
        this.entityClassName = requireText(entityClassName, "entityClassName");
        this.schema = trim(schema);
        this.tableName = requireText(tableName, "tableName");
        this.comment = trim(comment);
        this.tableSize = tableSize == null ? DdlTableSize.UNSET : tableSize;
        this.fields = immutableCopy(fields);
        this.indexes = immutableCopyIndexes(indexes);
        validate();
    }

    public String entityClassName() {
        return entityClassName;
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

    public DdlTableSize tableSize() {
        return tableSize;
    }

    public List<DdlFieldMetadata> fields() {
        return fields;
    }

    public List<DdlIndexMetadata> indexes() {
        return indexes;
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

    private static List<DdlFieldMetadata> immutableCopy(List<DdlFieldMetadata> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("fields must contain at least one persisted field");
        }
        List<DdlFieldMetadata> copy = new ArrayList<DdlFieldMetadata>(source.size());
        for (DdlFieldMetadata field : source) {
            if (field == null) {
                throw new IllegalArgumentException("fields must not contain null");
            }
            copy.add(field);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<DdlIndexMetadata> immutableCopyIndexes(List<DdlIndexMetadata> source) {
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

    private void validate() {
        Set<String> fieldNames = new HashSet<String>();
        Set<String> columnNames = new HashSet<String>();
        Set<String> persistedColumns = new HashSet<String>();
        Set<String> indexNames = new HashSet<String>();
        boolean hasPersistedField = false;
        for (DdlFieldMetadata field : fields) {
            if (!fieldNames.add(field.fieldName())) {
                throw new IllegalArgumentException("field name must not be duplicated: " + field.fieldName());
            }
            if (!columnNames.add(field.columnName())) {
                throw new IllegalArgumentException("column name must not be duplicated: " + field.columnName());
            }
            if (field.persisted()) {
                hasPersistedField = true;
                persistedColumns.add(field.columnName());
            }
        }
        if (!hasPersistedField) {
            throw new IllegalArgumentException("fields must contain at least one persisted field");
        }
        for (DdlIndexMetadata index : indexes) {
            if (!index.name().isEmpty() && !indexNames.add(index.name())) {
                throw new IllegalArgumentException("index name must not be duplicated: " + index.name());
            }
            if (!index.expression().isEmpty()) {
                continue;
            }
            for (String field : index.fields()) {
                if (!persistedColumns.contains(field)) {
                    throw new IllegalArgumentException("index field does not exist: " + field);
                }
            }
        }
    }
}
