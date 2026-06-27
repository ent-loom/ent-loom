package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.capability.exporting.DefaultExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImportExportFormatRegistryTest {
    @Test
    void importRegistryFailsClosedForUnknownFormat() {
        DefaultImportFormatRegistry registry = new DefaultImportFormatRegistry(Collections.singletonList(
            importDescriptor("excel-xlsx")
        ));

        CrudException ex = Assertions.assertThrows(CrudException.class, () -> registry.getRequired("csv"));

        Assertions.assertEquals(CrudErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    @Test
    void importRegistryRejectsDuplicateFormats() {
        CrudException ex = Assertions.assertThrows(CrudException.class, () ->
            new DefaultImportFormatRegistry(Arrays.asList(
                importDescriptor("excel-xlsx"),
                importDescriptor(" EXCEL-XLSX ")
            ))
        );

        Assertions.assertEquals(CrudErrorCode.ROUTE_AMBIGUOUS, ex.getErrorCode());
    }

    @Test
    void exportRegistryFailsClosedForUnknownFormat() {
        DefaultExportFormatRegistry registry = new DefaultExportFormatRegistry(Collections.singletonList(
            exportDescriptor("excel-xlsx")
        ));

        CrudException ex = Assertions.assertThrows(CrudException.class, () -> registry.getRequired("csv"));

        Assertions.assertEquals(CrudErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    private ImportFormatDescriptor importDescriptor(String format) {
        return new ImportFormatDescriptor(
            format,
            "Excel xlsx",
            "application/xlsx",
            "xlsx",
            (spec, content) -> new ImportParsedTable(Collections.<String>emptyList(), Collections.<ImportParsedTable.ImportParsedRow>emptyList()),
            (spec, result) -> FileWriteRequest.builder().fileName("errors.xlsx").content(new byte[0]).build()
        );
    }

    private ExportFormatDescriptor exportDescriptor(String format) {
        return new ExportFormatDescriptor(
            format,
            "Excel xlsx",
            "application/xlsx",
            "xlsx",
            (spec, rows) -> FileWriteRequest.builder().fileName("export.xlsx").content(new byte[0]).build()
        );
    }
}
