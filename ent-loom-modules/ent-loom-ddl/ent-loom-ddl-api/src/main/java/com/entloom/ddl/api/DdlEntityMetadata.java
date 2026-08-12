package com.entloom.ddl.api;

import com.entloom.ddl.enums.DdlTableSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 实体到表的元数据映射。
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
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DdlFieldMetadata>(source));
    }

    private static List<DdlIndexMetadata> immutableCopyIndexes(List<DdlIndexMetadata> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DdlIndexMetadata>(source));
    }
}
