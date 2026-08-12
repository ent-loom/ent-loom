package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.runtime.meta.EntityFieldMeta;

/**
 * 导出列合同。
 */
public final class ExportColumn {
    private final String key;
    private final String sourceField;
    private final String header;
    private final EntityFieldMeta fieldMeta;
    private final String format;

    public ExportColumn(String key, String sourceField, String header, EntityFieldMeta fieldMeta, String format) {
        this.key = key;
        this.sourceField = sourceField;
        this.header = header;
        this.fieldMeta = fieldMeta;
        this.format = format;
    }

    public String getKey() {
        return key;
    }

    public String getSourceField() {
        return sourceField;
    }

    public String getHeader() {
        return header;
    }

    public EntityFieldMeta getFieldMeta() {
        return fieldMeta;
    }

    public String getFormat() {
        return format;
    }
}
