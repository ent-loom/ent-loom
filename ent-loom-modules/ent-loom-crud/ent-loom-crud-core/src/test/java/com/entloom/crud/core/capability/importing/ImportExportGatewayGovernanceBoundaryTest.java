package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.spec.CommandExecutionSpec;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportFormatDescriptor;
import com.entloom.crud.core.capability.exporting.ExportFormatRegistry;
import com.entloom.crud.core.capability.exporting.ExportGatewayImpl;
import com.entloom.crud.core.capability.exporting.ExportPayloadCustomizer;
import com.entloom.crud.core.capability.exporting.ExportPayloadCustomizerRegistry;
import com.entloom.crud.core.capability.exporting.ExportResult;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.stats.StatsSpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.TaskFileAccessGuard;
import com.entloom.crud.core.foundation.taskfile.TaskService;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.governance.service.CrudGovernanceResult;
import com.entloom.crud.core.governance.service.CrudGovernanceService;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImportExportGatewayGovernanceBoundaryTest {
    @Test
    void import_denial_should_not_run_payload_customizer_or_format_registry() {
        RecordingImportCustomizer customizer = new RecordingImportCustomizer();
        RecordingImportFormatRegistry formatRegistry = new RecordingImportFormatRegistry(false);
        ImportGatewayImpl gateway = new ImportGatewayImpl(
            formatRegistry,
            new ImportPayloadCustomizerRegistry(Collections.<ImportPayloadCustomizer>singletonList(customizer)),
            null,
            null,
            new ExecutionPipeline(new DenyingGovernanceService()),
            new NoopTaskService(),
            new TaskFileAccessGuard()
        );

        Assertions.assertThrows(PermissionDeniedException.class, () -> gateway.submit(importSpec()));

        Assertions.assertEquals(0, customizer.calls.get());
        Assertions.assertEquals(0, formatRegistry.requiredCalls.get());
    }

    @Test
    void export_denial_should_not_run_payload_customizer_or_format_registry() {
        RecordingExportCustomizer customizer = new RecordingExportCustomizer();
        RecordingExportFormatRegistry formatRegistry = new RecordingExportFormatRegistry(false);
        ExportGatewayImpl gateway = new ExportGatewayImpl(
            formatRegistry,
            new ExportPayloadCustomizerRegistry(Collections.<ExportPayloadCustomizer>singletonList(customizer)),
            null,
            null,
            new ExecutionPipeline(new DenyingGovernanceService()),
            new NoopTaskService(),
            new TaskFileAccessGuard()
        );

        Assertions.assertThrows(PermissionDeniedException.class, () -> gateway.submit(exportSpec()));

        Assertions.assertEquals(0, customizer.calls.get());
        Assertions.assertEquals(0, formatRegistry.requiredCalls.get());
    }

    @Test
    void import_format_failure_after_governance_should_record_execution_failure() {
        RecordingGovernanceService governanceService = new RecordingGovernanceService();
        ImportGatewayImpl gateway = new ImportGatewayImpl(
            new RecordingImportFormatRegistry(true),
            new ImportPayloadCustomizerRegistry(Collections.<ImportPayloadCustomizer>emptyList()),
            null,
            null,
            new ExecutionPipeline(governanceService),
            new NoopTaskService(),
            new TaskFileAccessGuard()
        );

        CrudException ex = Assertions.assertThrows(CrudException.class, () -> gateway.submit(importSpec()));

        Assertions.assertEquals(CrudErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
        Assertions.assertEquals(1, governanceService.failureCalls.get());
        Assertions.assertEquals(0, governanceService.allowCalls.get());
    }

    @Test
    void import_payload_customizer_should_not_change_non_payload_fields() {
        ImportGatewayImpl gateway = new ImportGatewayImpl(
            new RecordingImportFormatRegistry(false),
            new ImportPayloadCustomizerRegistry(Collections.<ImportPayloadCustomizer>singletonList(new ImportPayloadCustomizer() {
                @Override
                public boolean supports(ImportSpec spec) {
                    return true;
                }

                @Override
                public ImportSpec customize(ImportSpec spec) {
                    return spec.toBuilder().scene("changed").build();
                }
            })),
            null,
            null,
            new ExecutionPipeline(new RecordingGovernanceService()),
            new NoopTaskService(),
            new TaskFileAccessGuard()
        );

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> gateway.submit(importSpec()));

        Assertions.assertTrue(ex.getMessage().contains("只能修改 payload"));
    }

    @Test
    void export_format_failure_after_governance_should_record_execution_failure() {
        RecordingGovernanceService governanceService = new RecordingGovernanceService();
        ExportGatewayImpl gateway = new ExportGatewayImpl(
            new RecordingExportFormatRegistry(true),
            new ExportPayloadCustomizerRegistry(Collections.<ExportPayloadCustomizer>emptyList()),
            null,
            null,
            new ExecutionPipeline(governanceService),
            new NoopTaskService(),
            new TaskFileAccessGuard()
        );

        CrudException ex = Assertions.assertThrows(CrudException.class, () -> gateway.submit(exportSpec()));

        Assertions.assertEquals(CrudErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
        Assertions.assertEquals(1, governanceService.failureCalls.get());
        Assertions.assertEquals(0, governanceService.allowCalls.get());
    }

    @Test
    void export_payload_customizer_should_not_change_non_payload_fields() {
        ExportGatewayImpl gateway = new ExportGatewayImpl(
            new RecordingExportFormatRegistry(false),
            new ExportPayloadCustomizerRegistry(Collections.<ExportPayloadCustomizer>singletonList(new ExportPayloadCustomizer() {
                @Override
                public boolean supports(ExportSpec spec) {
                    return true;
                }

                @Override
                public ExportSpec customize(ExportSpec spec) {
                    return spec.toBuilder().limit(10).build();
                }
            })),
            null,
            null,
            new ExecutionPipeline(new RecordingGovernanceService()),
            new NoopTaskService(),
            new TaskFileAccessGuard()
        );

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> gateway.submit(exportSpec()));

        Assertions.assertTrue(ex.getMessage().contains("只能修改 payload"));
    }

    private static ImportSpec importSpec() {
        return ImportSpec.builder()
            .rootType(Object.class)
            .format("xlsx")
            .sourceFile(FileRef.builder().fileId("source-file").build())
            .build();
    }

    private static ExportSpec exportSpec() {
        return ExportSpec.builder()
            .rootType(Object.class)
            .format("xlsx")
            .build();
    }

    private static SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId("tenant-a");
        subject.setOrgId("org-a");
        return subject;
    }

    private static final class RecordingImportCustomizer implements ImportPayloadCustomizer {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean supports(ImportSpec spec) {
            calls.incrementAndGet();
            return true;
        }

        @Override
        public ImportSpec customize(ImportSpec spec) {
            return spec;
        }
    }

    private static final class RecordingExportCustomizer implements ExportPayloadCustomizer {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean supports(ExportSpec spec) {
            calls.incrementAndGet();
            return true;
        }

        @Override
        public ExportSpec customize(ExportSpec spec) {
            return spec;
        }
    }

    private static final class RecordingImportFormatRegistry implements ImportFormatRegistry {
        private final AtomicInteger requiredCalls = new AtomicInteger();
        private final boolean fail;

        private RecordingImportFormatRegistry(boolean fail) {
            this.fail = fail;
        }

        @Override
        public ImportFormatDescriptor getRequired(String format) {
            requiredCalls.incrementAndGet();
            if (fail) {
                throw new CrudException(CrudErrorCode.UNSUPPORTED_FORMAT, "unsupported import format");
            }
            return null;
        }

        @Override
        public boolean supports(String format) {
            return !fail;
        }

        @Override
        public Collection<ImportFormatDescriptor> descriptors() {
            return Collections.emptyList();
        }
    }

    private static final class RecordingExportFormatRegistry implements ExportFormatRegistry {
        private final AtomicInteger requiredCalls = new AtomicInteger();
        private final boolean fail;

        private RecordingExportFormatRegistry(boolean fail) {
            this.fail = fail;
        }

        @Override
        public ExportFormatDescriptor getRequired(String format) {
            requiredCalls.incrementAndGet();
            if (fail) {
                throw new CrudException(CrudErrorCode.UNSUPPORTED_FORMAT, "unsupported export format");
            }
            return null;
        }

        @Override
        public boolean supports(String format) {
            return !fail;
        }

        @Override
        public Collection<ExportFormatDescriptor> descriptors() {
            return Collections.emptyList();
        }
    }

    private static final class NoopTaskService implements TaskService {
        @Override
        public CrudTask create(CrudTask task) {
            return task;
        }

        @Override
        public CrudTask getRequired(String taskId) {
            return CrudTask.builder().taskId(taskId).status(CrudTaskStatus.SUCCEEDED).build();
        }

        @Override
        public CrudTask updateStatus(String taskId, CrudTaskStatus status, String message) {
            return getRequired(taskId);
        }

        @Override
        public CrudTask cancel(String taskId, String reason) {
            return getRequired(taskId);
        }
    }

    private static class RecordingGovernanceService implements CrudGovernanceService {
        private final AtomicInteger allowCalls = new AtomicInteger();
        private final AtomicInteger failureCalls = new AtomicInteger();

        @Override
        public <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec) {
            ImportSpec effective = spec.withSubject(subject())
                .withGovernance(AccessDecision.ALLOW, CrudDataScope.allowAll(), CrudDataScope.allowAll());
            return new CrudGovernanceResult<ImportSpec>(
                subject(),
                new CrudResourceAction("Object", "IMPORT:SUBMIT", spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effective
            );
        }

        @Override
        public CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec) {
            ExportSpec effective = spec.withSubject(subject())
                .withGovernance(AccessDecision.ALLOW, CrudDataScope.allowAll(), CrudDataScope.allowAll());
            return new CrudGovernanceResult<ExportSpec>(
                subject(),
                new CrudResourceAction("Object", "EXPORT:SUBMIT", spec.getScene()),
                AccessDecision.ALLOW,
                CrudDataScope.allowAll(),
                CrudDataScope.allowAll(),
                System.currentTimeMillis(),
                effective
            );
        }

        @Override
        public void recordAllow(CrudGovernanceResult<?> result) {
            allowCalls.incrementAndGet();
        }

        @Override
        public void recordExecutionFailure(CrudGovernanceResult<?> result, Throwable throwable) {
            failureCalls.incrementAndGet();
        }
    }

    private static final class DenyingGovernanceService extends RecordingGovernanceService {
        @Override
        public CrudGovernanceResult<ImportSpec> governImport(ImportSpec spec) {
            throw new PermissionDeniedException("denied");
        }

        @Override
        public CrudGovernanceResult<ExportSpec> governExport(ExportSpec spec) {
            throw new PermissionDeniedException("denied");
        }
    }
}
