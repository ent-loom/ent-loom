package com.entloom.crud.core.capability.importing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 格式解析后的表格数据。
 */
public final class ImportParsedTable {
    private final List<String> headers;
    private final List<ImportParsedRow> rows;

    public ImportParsedTable(List<String> headers, List<ImportParsedRow> rows) {
        this.headers = Collections.unmodifiableList(copyStrings(headers));
        this.rows = Collections.unmodifiableList(copyRows(rows));
    }

    public List<String> getHeaders() {
        return copyStrings(headers);
    }

    public List<ImportParsedRow> getRows() {
        return copyRows(rows);
    }

    private static List<String> copyStrings(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }

    private static List<ImportParsedRow> copyRows(List<ImportParsedRow> source) {
        return source == null ? new ArrayList<ImportParsedRow>() : new ArrayList<ImportParsedRow>(source);
    }

    public static final class ImportParsedRow {
        private final int rowNumber;
        private final Map<String, Object> values;

        public ImportParsedRow(int rowNumber, Map<String, Object> values) {
            this.rowNumber = rowNumber;
            this.values = Collections.unmodifiableMap(copyValues(values));
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public Map<String, Object> getValues() {
            return copyValues(values);
        }

        private static Map<String, Object> copyValues(Map<String, Object> source) {
            return source == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
        }
    }
}
