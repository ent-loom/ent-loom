package com.entloom.crud.core.runtime.model;

import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import lombok.Getter;

/**
 * CRUD 运行期字段元数据。
 */
@Getter
public final class CrudRuntimeFieldModel {
    private final String fieldName;
    private final Class<?> javaType;
    private final String columnName;
    private final boolean nullable;
    private final boolean relation;
    private final boolean filterable;
    private final boolean sortable;
    private final boolean writable;
    private final boolean scopeField;
    private final boolean immutable;
    private final Boolean exportable;
    private final Boolean exportDefaultVisible;
    private final String exportLabel;
    private final String exportFormat;
    private final String dictionaryCode;
    private final String displayField;

    public CrudRuntimeFieldModel(
        String fieldName,
        Class<?> javaType,
        String columnName,
        boolean nullable,
        boolean relation,
        boolean filterable,
        boolean sortable,
        boolean writable,
        boolean scopeField,
        boolean immutable
    ) {
        this(
            fieldName,
            javaType,
            columnName,
            nullable,
            relation,
            filterable,
            sortable,
            writable,
            scopeField,
            immutable,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public CrudRuntimeFieldModel(
        String fieldName,
        Class<?> javaType,
        String columnName,
        boolean nullable,
        boolean relation,
        boolean filterable,
        boolean sortable,
        boolean writable,
        boolean scopeField,
        boolean immutable,
        Boolean exportable,
        Boolean exportDefaultVisible,
        String exportLabel,
        String exportFormat,
        String dictionaryCode,
        String displayField
    ) {
        this.fieldName = fieldName;
        this.javaType = javaType == null ? Object.class : javaType;
        this.columnName = columnName;
        this.nullable = nullable;
        this.relation = relation;
        this.filterable = filterable;
        this.sortable = sortable;
        this.writable = writable;
        this.scopeField = scopeField;
        this.immutable = immutable;
        this.exportable = exportable;
        this.exportDefaultVisible = exportDefaultVisible;
        this.exportLabel = exportLabel;
        this.exportFormat = exportFormat;
        this.dictionaryCode = dictionaryCode;
        this.displayField = displayField;
    }

    public static CrudRuntimeFieldModel from(EntityFieldMeta fieldMeta) {
        if (fieldMeta == null) {
            return null;
        }
        return new CrudRuntimeFieldModel(
            fieldMeta.getFieldName(),
            fieldMeta.getJavaType(),
            fieldMeta.getColumnName(),
            fieldMeta.isNullable(),
            fieldMeta.isRelation(),
            fieldMeta.isFilterable(),
            fieldMeta.isSortable(),
            fieldMeta.isWritable(),
            fieldMeta.isScopeField(),
            fieldMeta.isImmutable(),
            fieldMeta.getExportable(),
            fieldMeta.getExportDefaultVisible(),
            fieldMeta.getExportLabel(),
            fieldMeta.getExportFormat(),
            fieldMeta.getDictionaryCode(),
            fieldMeta.getDisplayField()
        );
    }

}
