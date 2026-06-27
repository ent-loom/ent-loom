package com.entloom.crud.excel;

import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.exporting.ExportTable;
import com.entloom.crud.core.capability.importing.ImportErrorFileWriter;
import com.entloom.crud.core.capability.importing.ImportResult;
import com.entloom.crud.core.capability.importing.ImportRowError;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes import row errors as xlsx.
 */
public class ExcelXlsxErrorFileWriter implements ImportErrorFileWriter {
    private final ExcelXlsxExportWriter exportWriter;

    public ExcelXlsxErrorFileWriter(ExcelXlsxExportWriter exportWriter) {
        this.exportWriter = Objects.requireNonNull(exportWriter, "exportWriter 不能为空");
    }

    @Override
    public FileWriteRequest writeErrorFile(ImportSpec spec, ImportResult result) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (ImportRowError error : result.getRowErrors()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("rowNumber", error.getRowNumber());
                row.put("field", error.getField());
                row.put("code", error.getCode());
                row.put("message", error.getMessage());
                rows.add(row);
            }
        }
        ExportSpec exportSpec = ExportSpec.builder()
            .rootType(spec == null ? null : spec.getRootType())
            .scene(spec == null ? null : spec.getScene())
            .format(ExcelXlsxSupport.FORMAT)
            .fileName(resolveFileName(spec))
            .build();
        return exportWriter.write(exportSpec, new ExportTable(errorColumns(), rows));
    }

    private List<ExportColumn> errorColumns() {
        List<ExportColumn> columns = new ArrayList<ExportColumn>();
        columns.add(new ExportColumn("rowNumber", "rowNumber", "rowNumber", null, null));
        columns.add(new ExportColumn("field", "field", "field", null, null));
        columns.add(new ExportColumn("code", "code", "code", null, null));
        columns.add(new ExportColumn("message", "message", "message", null, null));
        return columns;
    }

    private String resolveFileName(ImportSpec spec) {
        Class<?> rootType = spec == null ? null : spec.getRootType();
        String resource = rootType == null ? null : rootType.getSimpleName();
        String prefix = resource == null || resource.trim().isEmpty() ? "import" : resource.trim();
        return prefix + "-errors.xlsx";
    }
}
