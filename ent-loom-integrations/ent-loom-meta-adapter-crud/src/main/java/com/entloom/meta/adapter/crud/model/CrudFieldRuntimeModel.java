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
    private final boolean filterable;
    private final boolean sortable;
    private final boolean writable;
    private final boolean scopeField;
    private final boolean immutable;
    private final String label;
    private final boolean required;
    private final boolean inputRequired;
    private final Object createDefaultValue;

    public CrudFieldRuntimeModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable,
        boolean relation
    ) {
        this(fieldName, javaType, columnName, nullable, relation, true, true, true, false, false, null, false, false, null);
    }

    public CrudFieldRuntimeModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable,
        boolean relation,
        boolean filterable,
        boolean sortable,
        boolean writable,
        boolean scopeField,
        boolean immutable
    ) {
        this(fieldName, javaType, columnName, nullable, relation, filterable, sortable, writable,
            scopeField, immutable, null, false, false, null);
    }

    public CrudFieldRuntimeModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable,
        boolean relation,
        boolean filterable,
        boolean sortable,
        boolean writable,
        boolean scopeField,
        boolean immutable,
        String label,
        boolean required
    ) {
        this(fieldName, javaType, columnName, nullable, relation, filterable, sortable, writable,
            scopeField, immutable, label, required, required && writable, null);
    }

    public CrudFieldRuntimeModel(
        String fieldName,
        Class<?> javaType,
        SourcedValue<String> columnName,
        SourcedValue<Boolean> nullable,
        boolean relation,
        boolean filterable,
        boolean sortable,
        boolean writable,
        boolean scopeField,
        boolean immutable,
        String label,
        boolean required,
        boolean inputRequired,
        Object createDefaultValue
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.columnName = columnName;
        this.nullable = nullable;
        this.relation = relation;
        this.filterable = filterable;
        this.sortable = sortable;
        this.writable = writable;
        this.scopeField = scopeField;
        this.immutable = immutable;
        this.label = label;
        this.required = required;
        this.inputRequired = inputRequired;
        this.createDefaultValue = createDefaultValue;
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

    public boolean filterable() {
        return filterable;
    }

    public boolean sortable() {
        return sortable;
    }

    public boolean writable() {
        return writable;
    }

    public boolean scopeField() {
        return scopeField;
    }

    public boolean immutable() {
        return immutable;
    }

    public String label() {
        return label;
    }

    public boolean required() {
        return required;
    }

    public boolean inputRequired() {
        return inputRequired;
    }

    public Object createDefaultValue() {
        return createDefaultValue;
    }
}
