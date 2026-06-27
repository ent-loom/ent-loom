package com.entloom.crud.core.capability.exporting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出表格合同。
 */
public final class ExportTable {
    private final List<ExportColumn> columns;
    private final List<Map<String, Object>> rows;

    public ExportTable(List<ExportColumn> columns, List<Map<String, Object>> rows) {
        this.columns = Collections.unmodifiableList(copyColumns(columns));
        this.rows = Collections.unmodifiableList(copyRows(rows));
    }

    public List<ExportColumn> getColumns() {
        return copyColumns(columns);
    }

    public List<Map<String, Object>> getRows() {
        return copyRows(rows);
    }

    private static List<ExportColumn> copyColumns(List<ExportColumn> source) {
        return source == null ? new ArrayList<ExportColumn>() : new ArrayList<ExportColumn>(source);
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> source) {
        List<Map<String, Object>> target = new ArrayList<Map<String, Object>>();
        if (source == null) {
            return target;
        }
        for (Map<String, Object> row : source) {
            target.add(row == null ? null : new LinkedHashMap<String, Object>(row));
        }
        return target;
    }
}
