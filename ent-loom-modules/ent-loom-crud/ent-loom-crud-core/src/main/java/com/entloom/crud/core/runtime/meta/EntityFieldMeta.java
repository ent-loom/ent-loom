package com.entloom.crud.core.runtime.meta;

import lombok.Getter;

/**
 * 实体字段元数据。
 */
@Getter
public class EntityFieldMeta {
    /** 字段名。 */
    private final String fieldName;
    /** Java 类型。 */
    private final Class<?> javaType;
    /** 列名。 */
    private final String columnName;
    /** 是否可为空。 */
    private final boolean nullable;
    /** 是否为关系字段。 */
    private final boolean relation;
    /** 是否允许过滤。 */
    private final boolean filterable;
    /** 是否允许排序。 */
    private final boolean sortable;
    /** 是否允许普通写入。 */
    private final boolean writable;
    /** 是否为治理范围字段。 */
    private final boolean scopeField;
    /** 是否为不可变字段。 */
    private final boolean immutable;
    /** 是否允许导出；null 表示未配置，按默认规则降级。 */
    private final Boolean exportable;
    /** 默认导出时是否展示；null 表示未配置，按默认规则降级。 */
    private final Boolean exportDefaultVisible;
    /** 导出表头。 */
    private final String exportLabel;
    /** 导出格式提示。 */
    private final String exportFormat;
    /** 字典编码。 */
    private final String dictionaryCode;
    /** 外键引用对应的同表展示字段。 */
    private final String displayField;

    public EntityFieldMeta(
        String fieldName,
        Class<?> javaType,
        String columnName,
        boolean nullable,
        boolean relation,
        boolean filterable,
        boolean sortable
    ) {
        this(fieldName, javaType, columnName, nullable, relation, filterable, sortable, true, false, false);
    }

    public EntityFieldMeta(
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

    public EntityFieldMeta(
        String fieldName,
        Class<?> javaType,
        String columnName,
        boolean nullable,
        boolean relation,
        boolean filterable,
        boolean sortable,
        Boolean exportable,
        Boolean exportDefaultVisible,
        String exportLabel,
        String exportFormat,
        String dictionaryCode,
        String displayField
    ) {
        this(
            fieldName,
            javaType,
            columnName,
            nullable,
            relation,
            filterable,
            sortable,
            true,
            false,
            false,
            exportable,
            exportDefaultVisible,
            exportLabel,
            exportFormat,
            dictionaryCode,
            displayField
        );
    }

    public EntityFieldMeta(
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
        this.javaType = javaType;
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
        this.exportLabel = trimToNull(exportLabel);
        this.exportFormat = trimToNull(exportFormat);
        this.dictionaryCode = trimToNull(dictionaryCode);
        this.displayField = trimToNull(displayField);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
