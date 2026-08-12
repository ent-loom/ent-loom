package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.model.CrudExportData;
import com.entloom.crud.api.model.CrudFileData;
import com.entloom.crud.core.capability.exporting.ExportColumn;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudImportExportResponseAssemblerTest {
    @Test
    void file_data_should_only_expose_public_file_attributes() {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("purpose", "EXPORT_RESULT");
        attributes.put("format", "excel-xlsx");
        attributes.put("checksumSha256", "secret-checksum");
        attributes.put("storageKey", "local/path/result.xlsx");

        FileRef file = FileRef.builder()
            .fileId("F1")
            .fileName("result.xlsx")
            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .size(Long.valueOf(10L))
            .attributes(attributes)
            .build();

        CrudFileData data = new CrudImportExportResponseAssembler().fileData(file);

        Assertions.assertEquals("F1", data.getFileId());
        Assertions.assertEquals("EXPORT_RESULT", data.getAttributes().get("purpose"));
        Assertions.assertEquals("excel-xlsx", data.getAttributes().get("format"));
        Assertions.assertFalse(data.getAttributes().containsKey("checksumSha256"));
        Assertions.assertFalse(data.getAttributes().containsKey("storageKey"));
    }

    @Test
    void export_data_should_expose_columns_contract() {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("orderNo", "ORD-1");
        ExportResult result = ExportResult.builder()
            .columns(Collections.singletonList(new ExportColumn("orderNo", "orderNo", "订单号", null, null)))
            .previewRows(Collections.singletonList(row))
            .build();

        CrudExportData data = new CrudImportExportResponseAssembler().exportData(result);

        Assertions.assertEquals("orderNo", data.getColumns().get(0).getKey());
        Assertions.assertEquals("订单号", data.getColumns().get(0).getHeader());
        Assertions.assertEquals("ORD-1", data.getPreviewRows().get(0).get("orderNo"));
    }
}
