package com.entloom.crud.starter;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.exporting.ExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportGateway;
import com.entloom.crud.core.capability.importing.ImportFormatRegistry;
import com.entloom.crud.core.capability.importing.ImportGateway;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.foundation.taskfile.FileService;
import com.entloom.crud.core.foundation.taskfile.TaskFileAccessGuard;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.starter.config.CrudAutoConfiguration;
import com.entloom.crud.starter.support.StarterJdbcTestSupportConfiguration;
import com.entloom.crud.starter.web.assembler.CrudImportExportResponseAssembler;
import com.entloom.crud.starter.web.assembler.CrudImportExportSpecAssembler;
import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudExportController;
import com.entloom.crud.starter.web.controller.EntCrudImportController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.entloom.crud.starter.web.facade.EntCrudExportFacade;
import com.entloom.crud.starter.web.facade.EntCrudImportFacade;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrudModuleToggleAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CrudAutoConfiguration.class, StarterJdbcTestSupportConfiguration.class)
        .withPropertyValues(
            "entloom.crud.controller.enabled=true",
            "entloom.crud.import-export.storage-directory=target/entloom-crud-starter-test"
        );

    @Test
    void should_support_command_only_mode() {
        contextRunner
            .withPropertyValues("entloom.crud.query.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(QueryGateway.class);
                assertThat(context).doesNotHaveBean(EntCrudQueryController.class);
                assertThat(context).hasSingleBean(CommandGateway.class);
                assertThat(context).hasSingleBean(EntCrudCommandController.class);
            });
    }

    @Test
    void should_support_query_only_mode() {
        contextRunner
            .withPropertyValues("entloom.crud.command.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(QueryGateway.class);
                assertThat(context).hasSingleBean(EntCrudQueryController.class);
                assertThat(context).doesNotHaveBean(CommandGateway.class);
                assertThat(context).doesNotHaveBean(EntCrudCommandController.class);
            });
    }

    @Test
    void should_register_import_export_main_chain_by_default() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImportFormatRegistry.class);
            assertThat(context).hasSingleBean(ExportFormatRegistry.class);
            assertThat(context).hasSingleBean(ImportGateway.class);
            assertThat(context).hasSingleBean(ExportGateway.class);
            assertThat(context).hasSingleBean(FileService.class);
            assertThat(context).hasSingleBean(TaskService.class);
            assertThat(context).hasSingleBean(TaskFileAccessGuard.class);
            assertThat(context).hasSingleBean(CrudImportExportSpecAssembler.class);
            assertThat(context).hasSingleBean(CrudImportExportResponseAssembler.class);
            assertThat(context).hasSingleBean(EntCrudImportController.class);
            assertThat(context).hasSingleBean(EntCrudExportController.class);
            assertThat(context).hasSingleBean(EntCrudImportFacade.class);
            assertThat(context).hasSingleBean(EntCrudExportFacade.class);
        });
    }

    @Test
    void should_support_import_export_toggle() {
        contextRunner
            .withPropertyValues("entloom.crud.import.enabled=false", "entloom.crud.export.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(ImportFormatRegistry.class);
                assertThat(context).doesNotHaveBean(ExportFormatRegistry.class);
                assertThat(context).doesNotHaveBean(ImportGateway.class);
                assertThat(context).doesNotHaveBean(ExportGateway.class);
                assertThat(context).doesNotHaveBean(FileService.class);
                assertThat(context).doesNotHaveBean(TaskService.class);
                assertThat(context).doesNotHaveBean(TaskFileAccessGuard.class);
                assertThat(context).doesNotHaveBean(CrudImportExportSpecAssembler.class);
                assertThat(context).doesNotHaveBean(CrudImportExportResponseAssembler.class);
                assertThat(context).doesNotHaveBean(EntCrudImportController.class);
                assertThat(context).doesNotHaveBean(EntCrudExportController.class);
                assertThat(context).doesNotHaveBean(EntCrudImportFacade.class);
                assertThat(context).doesNotHaveBean(EntCrudExportFacade.class);
            });
    }

    @Test
    void should_support_import_only_mode() {
        contextRunner
            .withPropertyValues("entloom.crud.export.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(ImportFormatRegistry.class);
                assertThat(context).hasSingleBean(ImportGateway.class);
                assertThat(context).hasSingleBean(EntCrudImportController.class);
                assertThat(context).hasSingleBean(EntCrudImportFacade.class);
                assertThat(context).doesNotHaveBean(ExportFormatRegistry.class);
                assertThat(context).doesNotHaveBean(ExportGateway.class);
                assertThat(context).doesNotHaveBean(EntCrudExportController.class);
                assertThat(context).doesNotHaveBean(EntCrudExportFacade.class);
                assertThat(context).hasSingleBean(FileService.class);
                assertThat(context).hasSingleBean(TaskService.class);
                assertThat(context).hasSingleBean(TaskFileAccessGuard.class);
                assertThat(context).hasSingleBean(CrudImportExportSpecAssembler.class);
                assertThat(context).hasSingleBean(CrudImportExportResponseAssembler.class);
            });
    }

    @Test
    void should_support_export_only_mode() {
        contextRunner
            .withPropertyValues("entloom.crud.import.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(ExportFormatRegistry.class);
                assertThat(context).hasSingleBean(ExportGateway.class);
                assertThat(context).hasSingleBean(EntCrudExportController.class);
                assertThat(context).hasSingleBean(EntCrudExportFacade.class);
                assertThat(context).doesNotHaveBean(ImportFormatRegistry.class);
                assertThat(context).doesNotHaveBean(ImportGateway.class);
                assertThat(context).doesNotHaveBean(EntCrudImportController.class);
                assertThat(context).doesNotHaveBean(EntCrudImportFacade.class);
                assertThat(context).hasSingleBean(FileService.class);
                assertThat(context).hasSingleBean(TaskService.class);
                assertThat(context).hasSingleBean(TaskFileAccessGuard.class);
                assertThat(context).hasSingleBean(CrudImportExportSpecAssembler.class);
                assertThat(context).hasSingleBean(CrudImportExportResponseAssembler.class);
            });
    }

    @Test
    void should_not_register_http_controllers_when_controller_disabled() {
        new ApplicationContextRunner()
            .withUserConfiguration(CrudAutoConfiguration.class, StarterJdbcTestSupportConfiguration.class)
            .withPropertyValues("entloom.crud.controller.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(ImportGateway.class);
                assertThat(context).hasSingleBean(ExportGateway.class);
                assertThat(context).hasSingleBean(EntCrudImportFacade.class);
                assertThat(context).hasSingleBean(EntCrudExportFacade.class);
                assertThat(context).doesNotHaveBean(EntCrudImportController.class);
                assertThat(context).doesNotHaveBean(EntCrudExportController.class);
                assertThat(context).doesNotHaveBean(EntCrudQueryController.class);
                assertThat(context).doesNotHaveBean(EntCrudCommandController.class);
            });
    }

    @Test
    void should_start_without_excel_module_and_reject_excel_xlsx_format() {
        contextRunner.run(context -> {
            assertThat(ClassUtils.isPresent("com.entloom.crud.excel.ExcelXlsxSupport", context.getClassLoader()))
                .isFalse();
            assertThatThrownBy(() -> context.getBean(ImportFormatRegistry.class).getRequired("excel-xlsx"))
                .isInstanceOf(CrudException.class)
                .hasFieldOrPropertyWithValue("errorCode", CrudErrorCode.UNSUPPORTED_FORMAT);
            assertThatThrownBy(() -> context.getBean(ExportFormatRegistry.class).getRequired("excel-xlsx"))
                .isInstanceOf(CrudException.class)
                .hasFieldOrPropertyWithValue("errorCode", CrudErrorCode.UNSUPPORTED_FORMAT);
        });
    }
}
