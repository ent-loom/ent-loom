package com.entloom.crud.excel.config;

import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.capability.importing.ImportFormatDescriptor;
import com.entloom.crud.excel.ExcelXlsxErrorFileWriter;
import com.entloom.crud.excel.ExcelXlsxExportWriter;
import com.entloom.crud.excel.ExcelXlsxImportParser;
import com.entloom.crud.excel.ExcelXlsxSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Excel import/export auto-configuration entry.
 */
@Configuration
public class ExcelImportExportAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ExcelXlsxImportParser excelXlsxImportParser() {
        return new ExcelXlsxImportParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExcelXlsxExportWriter excelXlsxExportWriter() {
        return new ExcelXlsxExportWriter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExcelXlsxErrorFileWriter excelXlsxErrorFileWriter(ExcelXlsxExportWriter exportWriter) {
        return new ExcelXlsxErrorFileWriter(exportWriter);
    }

    @Bean
    public ImportFormatDescriptor excelXlsxImportFormatDescriptor(
        ExcelXlsxImportParser parser,
        ExcelXlsxErrorFileWriter errorFileWriter
    ) {
        return new ImportFormatDescriptor(
            ExcelXlsxSupport.FORMAT,
            "Excel xlsx",
            ExcelXlsxSupport.CONTENT_TYPE,
            ExcelXlsxSupport.EXTENSION,
            parser,
            errorFileWriter
        );
    }

    @Bean
    public ExportFormatDescriptor excelXlsxExportFormatDescriptor(ExcelXlsxExportWriter writer) {
        return new ExportFormatDescriptor(
            ExcelXlsxSupport.FORMAT,
            "Excel xlsx",
            ExcelXlsxSupport.CONTENT_TYPE,
            ExcelXlsxSupport.EXTENSION,
            writer
        );
    }
}
