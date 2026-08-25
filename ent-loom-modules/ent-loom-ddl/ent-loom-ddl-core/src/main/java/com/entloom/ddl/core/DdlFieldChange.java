package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlFieldMetadata;

/**
 * 单个字段的有限修改计划。
 */
public final class DdlFieldChange {
    private final String existingColumnName;
    private final DdlFieldMetadata desiredField;
    private final boolean existingAutoIncrement;

    public DdlFieldChange(String existingColumnName, DdlFieldMetadata desiredField) {
        this(existingColumnName, desiredField, false);
    }

    public DdlFieldChange(String existingColumnName,
                          DdlFieldMetadata desiredField,
                          boolean existingAutoIncrement) {
        if (existingColumnName == null || existingColumnName.trim().isEmpty()) {
            throw new IllegalArgumentException("existingColumnName must not be blank");
        }
        if (desiredField == null) {
            throw new IllegalArgumentException("desiredField must not be null");
        }
        this.existingColumnName = existingColumnName.trim();
        this.desiredField = desiredField;
        this.existingAutoIncrement = existingAutoIncrement;
    }

    public String existingColumnName() {
        return existingColumnName;
    }

    public DdlFieldMetadata desiredField() {
        return desiredField;
    }

    public boolean existingAutoIncrement() {
        return existingAutoIncrement;
    }

    public boolean renamed() {
        return !existingColumnName.equals(desiredField.columnName());
    }
}
