package com.entloom.crud.core.capability.exporting;

/**
 * 导出单元格展示值格式化器。
 */
public interface ExportValueFormatter {
    boolean supports(ExportFieldContext context, Object value);

    Object format(ExportFieldContext context, Object value);
}
