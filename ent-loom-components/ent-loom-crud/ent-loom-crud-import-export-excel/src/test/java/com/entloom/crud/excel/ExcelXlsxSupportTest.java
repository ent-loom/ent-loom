package com.entloom.crud.excel;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.exporting.ExportTable;
import com.entloom.crud.core.capability.importing.ImportParsedTable;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExcelXlsxSupportTest {
    @Test
    void writerAndParserRoundTripInlineStrings() {
        ExcelXlsxExportWriter writer = new ExcelXlsxExportWriter();
        ExcelXlsxImportParser parser = new ExcelXlsxImportParser();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", "Alice");
        row.put("phone", "13800138000");

        FileWriteRequest request = writer.write(
            ExportSpec.builder().format(ExcelXlsxSupport.FORMAT).fileName("users").build(),
            new ExportTable(columns("name", "phone"), Arrays.asList(row))
        );

        ImportParsedTable table = parser.parse(
            ImportSpec.builder().format(ExcelXlsxSupport.FORMAT).build(),
            request.getContent()
        );

        Assertions.assertEquals("users.xlsx", request.getFileName());
        Assertions.assertEquals(Arrays.asList("name", "phone"), table.getHeaders());
        Assertions.assertEquals(1, table.getRows().size());
        Assertions.assertEquals("Alice", table.getRows().get(0).getValues().get("name"));
        Assertions.assertEquals("13800138000", table.getRows().get(0).getValues().get("phone"));
    }

    @Test
    void writerEscapesFormulaLikeValues() {
        ExcelXlsxExportWriter writer = new ExcelXlsxExportWriter();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", "=SUM(A1:A2)");

        FileWriteRequest request = writer.write(
            ExportSpec.builder().build(),
            new ExportTable(columns("name"), Arrays.asList(row))
        );

        ImportParsedTable table = new ExcelXlsxImportParser().parse(ImportSpec.builder().build(), request.getContent());
        Assertions.assertEquals("'=SUM(A1:A2)", table.getRows().get(0).getValues().get("name"));
    }

    @Test
    void writerPreservesPrecisionSensitiveNumbersAsText() {
        ExcelXlsxExportWriter writer = new ExcelXlsxExportWriter();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("safeCount", Long.valueOf(123456789012345L));
        row.put("largeId", Long.valueOf(1234567890123456L));
        row.put("amount", new BigDecimal("1234567890.123456789"));
        row.put("bigInteger", new BigInteger("98765432109876543210"));

        FileWriteRequest request = writer.write(
            ExportSpec.builder().build(),
            new ExportTable(columns("safeCount", "largeId", "amount", "bigInteger"), Arrays.asList(row))
        );

        try {
            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(request.getContent()));
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            Assertions.assertEquals(CellType.NUMERIC, dataRow.getCell(0).getCellType());
            Assertions.assertEquals(Double.valueOf(123456789012345D), Double.valueOf(dataRow.getCell(0).getNumericCellValue()));
            Assertions.assertEquals(CellType.STRING, dataRow.getCell(1).getCellType());
            Assertions.assertEquals("1234567890123456", dataRow.getCell(1).getStringCellValue());
            Assertions.assertEquals(CellType.STRING, dataRow.getCell(2).getCellType());
            Assertions.assertEquals("1234567890.123456789", dataRow.getCell(2).getStringCellValue());
            Assertions.assertEquals(CellType.STRING, dataRow.getCell(3).getCellType());
            Assertions.assertEquals("98765432109876543210", dataRow.getCell(3).getStringCellValue());
            workbook.close();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void parserRejectsFormulaCells() {
        byte[] formulaWorkbook = formulaWorkbook();

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> new ExcelXlsxImportParser().parse(ImportSpec.builder().build(), formulaWorkbook)
        );

        Assertions.assertEquals(CrudErrorCode.ROW_VALIDATION_FAILED, ex.getErrorCode());
        Assertions.assertEquals("FORMULA_NOT_ALLOWED", ex.getReason());
    }

    private byte[] formulaWorkbook() {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellFormula("SUM(A1:A1)");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<ExportColumn> columns(String... fields) {
        List<ExportColumn> columns = new java.util.ArrayList<ExportColumn>();
        for (String field : fields) {
            columns.add(new ExportColumn(field, field, field, null, null));
        }
        return columns;
    }
}
