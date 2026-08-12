package com.entloom.crud.core.capability.exporting;

import java.util.List;
import java.util.Map;

/**
 * 导出行展示值渲染器。
 */
public interface ExportValueRenderer {
    Map<String, Object> renderRow(List<ExportColumn> columns, Map<String, Object> rawRow, ExportRenderOptions options);
}
