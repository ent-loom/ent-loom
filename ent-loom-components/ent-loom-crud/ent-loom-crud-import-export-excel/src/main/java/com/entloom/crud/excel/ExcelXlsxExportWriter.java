package com.entloom.crud.excel;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.ExportFileWriter;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.exporting.ExportTable;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * XLSX writer backed by Apache POI and isolated in the Excel module.
 */
public class ExcelXlsxExportWriter implements ExportFileWriter {
    private static final int STREAMING_WINDOW_SIZE = 100;

    @Override
    public FileWriteRequest write(ExportSpec spec, ExportTable table) {
        if (table == null || table.getColumns().isEmpty()) {
            throw new ValidationException("导出列合同不能为空");
        }
        byte[] content = writeWorkbook(table.getColumns(), table.getRows());
        return FileWriteRequest.builder()
            .fileName(resolveFileName(spec))
            .contentType(ExcelXlsxSupport.CONTENT_TYPE)
            .content(content)
            .build();
    }

    byte[] writeWorkbook(List<ExportColumn> columns, List<Map<String, Object>> rows) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");
            appendHeader(sheet.createRow(0), columns);
            int rowNumber = 1;
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    appendDataRow(sheet.createRow(rowNumber++), columns, row);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "生成 xlsx 失败", ex);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // POI close failures after write do not change the generated payload.
            }
        }
    }

    private void appendHeader(Row row, List<ExportColumn> columns) {
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i, CellType.STRING);
            cell.setCellValue(columns.get(i).getHeader());
        }
    }

    private void appendDataRow(Row excelRow, List<ExportColumn> columns, Map<String, Object> row) {
        for (int i = 0; i < columns.size(); i++) {
            appendCell(excelRow, i, value(row, columns.get(i).getKey()));
        }
    }

    private String resolveFileName(ExportSpec spec) {
        String requested = spec == null ? null : spec.getFileName();
        String name = requested == null || requested.trim().isEmpty() ? "export.xlsx" : requested.trim();
        name = name.replace('\\', '_').replace('/', '_').replace("..", "_");
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            safe.append(Character.isISOControl(ch) ? '_' : ch);
        }
        String result = safe.toString();
        return result.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? result : result + ".xlsx";
    }

    private void appendCell(Row excelRow, int index, Object value) {
        if (isSafeNumericCellValue(value)) {
            Cell cell = excelRow.createCell(index, CellType.NUMERIC);
            cell.setCellValue(((Number) value).doubleValue());
            return;
        }
        Cell cell = excelRow.createCell(index, CellType.STRING);
        cell.setCellValue(escapeFormula(value == null ? "" : String.valueOf(value)));
    }

    private boolean isSafeNumericCellValue(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        if (value instanceof BigDecimal || value instanceof BigInteger || value instanceof AtomicLong) {
            return false;
        }
        if (value instanceof Long) {
            return hasExcelSafeIntegerPrecision(((Long) value).longValue());
        }
        if (value instanceof AtomicInteger) {
            return true;
        }
        return value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Float
            || value instanceof Double;
    }

    private boolean hasExcelSafeIntegerPrecision(long value) {
        long normalized = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        return String.valueOf(normalized).length() <= 15;
    }

    private Object value(Map<String, Object> row, String column) {
        if (row == null) {
            return "";
        }
        return row.get(column);
    }

    static String escapeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
