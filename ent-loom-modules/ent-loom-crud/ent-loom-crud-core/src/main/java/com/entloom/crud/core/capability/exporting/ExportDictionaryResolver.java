package com.entloom.crud.core.capability.exporting;

/**
 * 导出字典展示值解析器。
 */
public interface ExportDictionaryResolver {
    /**
     * 返回字典展示文本；返回 null 表示未命中字典，继续走默认渲染链。
     */
    String resolve(ExportFieldContext context, Object value);
}
