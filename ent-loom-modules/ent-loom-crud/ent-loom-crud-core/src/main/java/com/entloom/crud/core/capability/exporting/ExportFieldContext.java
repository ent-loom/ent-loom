package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.core.runtime.meta.EntityFieldMeta;

/**
 * 导出字段渲染上下文。
 */
public final class ExportFieldContext {
    private final ExportColumn column;
    private final EntityFieldMeta fieldMeta;
    private final Class<?> declaredType;
    private final ExportRenderOptions renderOptions;

    public ExportFieldContext(ExportColumn column, ExportRenderOptions renderOptions) {
        this.column = column;
        this.fieldMeta = column == null ? null : column.getFieldMeta();
        this.declaredType = fieldMeta == null ? null : fieldMeta.getJavaType();
        this.renderOptions = renderOptions;
    }

    public ExportColumn getColumn() {
        return column;
    }

    public EntityFieldMeta getFieldMeta() {
        return fieldMeta;
    }

    public Class<?> getDeclaredType() {
        return declaredType;
    }

    public ExportRenderOptions getRenderOptions() {
        return renderOptions;
    }
}
