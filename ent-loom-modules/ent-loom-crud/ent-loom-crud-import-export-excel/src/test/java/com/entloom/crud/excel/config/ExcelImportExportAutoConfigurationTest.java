package com.entloom.crud.excel.config;

import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.capability.importing.ImportFormatDescriptor;
import com.entloom.crud.excel.ExcelXlsxErrorFileWriter;
import com.entloom.crud.excel.ExcelXlsxExportWriter;
import com.entloom.crud.excel.ExcelXlsxImportParser;
import com.entloom.crud.excel.ExcelXlsxSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ExcelImportExportAutoConfigurationTest {
    @Test
    void should_register_xlsx_format_descriptors_when_excel_module_present() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.register(ExcelImportExportAutoConfiguration.class);
            context.refresh();

            Assertions.assertNotNull(context.getBean(ExcelXlsxImportParser.class));
            Assertions.assertNotNull(context.getBean(ExcelXlsxExportWriter.class));
            Assertions.assertNotNull(context.getBean(ExcelXlsxErrorFileWriter.class));

            ImportFormatDescriptor importDescriptor = context.getBean(ImportFormatDescriptor.class);
            ExportFormatDescriptor exportDescriptor = context.getBean(ExportFormatDescriptor.class);
            Assertions.assertEquals(ExcelXlsxSupport.FORMAT, importDescriptor.getFormat());
            Assertions.assertEquals(ExcelXlsxSupport.CONTENT_TYPE, importDescriptor.getContentType());
            Assertions.assertEquals(ExcelXlsxSupport.EXTENSION, importDescriptor.getFileExtension());
            Assertions.assertEquals(ExcelXlsxSupport.FORMAT, exportDescriptor.getFormat());
            Assertions.assertEquals(ExcelXlsxSupport.CONTENT_TYPE, exportDescriptor.getContentType());
            Assertions.assertEquals(ExcelXlsxSupport.EXTENSION, exportDescriptor.getFileExtension());
        } finally {
            context.close();
        }
    }

    @Test
    void should_publish_boot_auto_configuration_import() throws IOException {
        InputStream inputStream = Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        Assertions.assertNotNull(inputStream);
        String content;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            content = reader.lines().collect(Collectors.joining("\n"));
        }
        Assertions.assertTrue(content.contains(ExcelImportExportAutoConfiguration.class.getName()));
    }

    @Test
    void should_publish_legacy_boot_auto_configuration_factory() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Assertions.assertTrue(SpringFactoriesLoader
            .loadFactoryNames(EnableAutoConfiguration.class, classLoader)
            .contains(ExcelImportExportAutoConfiguration.class.getName()));
    }
}
