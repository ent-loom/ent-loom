package com.entloom.crud.core.runtime.model.input;

import com.entloom.meta.contract.value.SourcedValue;

/**
 * CRUD native 字段中间模型。
 */
public final class CrudNativeFieldModel {
    private final String fieldName;
    private final Class<?> javaType;
    private final SourcedValue<String> columnName;
    private final SourcedValue<Boolean> nullable;
    private final SourcedValue<Boolean> writable;

    public CrudNativeFieldModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable
    ) {
        this(fieldName, javaType, columnName, nullable, SourcedValue.inferred(Boolean.TRUE));
    }

    public CrudNativeFieldModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable,
        SourcedValue<Boolean> writable
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.columnName = columnName;
        this.nullable = nullable;
        this.writable = writable;
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

    public SourcedValue<Boolean> writable() {
        return writable;
    }
}
