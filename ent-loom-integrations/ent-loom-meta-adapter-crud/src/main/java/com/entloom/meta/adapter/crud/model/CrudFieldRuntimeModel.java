package com.entloom.meta.adapter.crud.model;

import com.entloom.meta.contract.value.SourcedValue;

/**
 * CRUD 合并后的字段模型。
 */
public final class CrudFieldRuntimeModel {
    private final String fieldName;
    private final Class<?> javaType;
    private final SourcedValue<String> columnName;
    private final SourcedValue<Boolean> nullable;
    private final boolean relation;

    public CrudFieldRuntimeModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable,
        boolean relation
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.columnName = columnName;
        this.nullable = nullable;
        this.relation = relation;
    }

    public String fieldName() {
        return fieldName;
    }

    public Class<?> javaType() {
        return javaType;
    }

    public SourcedValue<String> columnName() {
        return columnName;
    }

    public SourcedValue<Boolean> nullable() {
        return nullable;
    }

    public boolean relation() {
        return relation;
    }
}
