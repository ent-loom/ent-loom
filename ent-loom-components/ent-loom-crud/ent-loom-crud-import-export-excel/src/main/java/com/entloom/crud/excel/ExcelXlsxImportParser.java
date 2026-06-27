package com.entloom.crud.excel;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.capability.importing.ImportFileParser;
import com.entloom.crud.core.capability.importing.ImportParsedTable;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * XLSX parser backed by Apache POI and isolated in the Excel module.
 */
public class ExcelXlsxImportParser implements ImportFileParser {
    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public ImportParsedTable parse(ImportSpec spec, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("导入文件内容不能为空");
        }
        try {
            Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content));
            try {
                if (workbook.getNumberOfSheets() < 1) {
                    throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "xlsx 缺少工作表");
                }
                return parseSheet(workbook.getSheetAt(0));
            } finally {
                workbook.close();
            }
        } catch (IOException ex) {
            throw new CrudException(CrudErrorCode.FILE_METADATA_INVALID, "读取 xlsx 失败", ex);
        }
    }

    private ImportParsedTable parseSheet(Sheet sheet) {
        Row headerRow = firstNonEmptyRow(sheet);
        if (headerRow == null) {
            throw new ValidationException("导入表头不能为空");
        }
        List<String> headers = readHeaders(headerRow);
        List<ImportParsedTable.ImportParsedRow> rows = new ArrayList<ImportParsedTable.ImportParsedRow>();
        for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isEmpty(row, headers.size())) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), readCell(row.getCell(column), row.getRowNum() + 1));
            }
            rows.add(new ImportParsedTable.ImportParsedRow(row.getRowNum() + 1, values));
        }
        return new ImportParsedTable(headers, rows);
    }

    private Row firstNonEmptyRow(Sheet sheet) {
        for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isEmpty(row, Math.max(row.getLastCellNum(), 0))) {
                return row;
            }
        }
        return null;
    }

    private List<String> readHeaders(Row row) {
        List<String> headers = new ArrayList<String>();
        int last = row.getLastCellNum();
        for (int column = 0; column < last; column++) {
            String header = readCell(row.getCell(column), row.getRowNum() + 1);
            if (header == null || header.trim().isEmpty()) {
                throw new ValidationException("导入表头不能为空: column=" + (column + 1));
            }
            String normalized = header.trim();
            if (headers.contains(normalized)) {
                throw new ValidationException("导入表头重复: " + normalized);
            }
            headers.add(normalized);
        }
        if (headers.isEmpty()) {
            throw new ValidationException("导入表头不能为空");
        }
        return headers;
    }

    private boolean isEmpty(Row row, int columnCount) {
        for (int column = 0; column < columnCount; column++) {
            Cell cell = row.getCell(column);
            if (cell != null && cell.getCellType() != CellType.BLANK && !dataFormatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Cell cell, int rowNumber) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            throw new CrudException(CrudErrorCode.ROW_VALIDATION_FAILED, "导入文件包含公式单元格: row=" + rowNumber)
                .withReason("FORMULA_NOT_ALLOWED");
        }
        return dataFormatter.formatCellValue(cell);
    }
}
