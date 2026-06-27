package com.entloom.meta.adapter.crud.model;

import com.entloom.meta.contract.value.SourcedValue;

/**
 * CRUD native 字段中间模型。
 */
public final class CrudNativeFieldModel {
    private final String fieldName;
    private final Class<?> javaType;
    private final SourcedValue<String> columnName;
    private final SourcedValue<Boolean> nullable;

    public CrudNativeFieldModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.columnName = columnName;
        this.nullable = nullable;
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
}
