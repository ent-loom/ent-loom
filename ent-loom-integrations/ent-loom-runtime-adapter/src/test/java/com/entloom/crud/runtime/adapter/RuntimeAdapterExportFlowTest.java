package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskContextSnapshot;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;
import com.entloom.crud.core.foundation.taskfile.TaskFileAccessGuard;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.runtime.inmemory.file.InMemoryFileStore;
import com.entloom.runtime.inmemory.task.InMemoryTaskStore;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeAdapterExportFlowTest {
    @Test
    void runtimeStoresSatisfyCrudTaskFileAdapterContract() throws Exception {
        InMemoryFileStore runtimeFiles = new InMemoryFileStore();
        RuntimeFileServiceAdapter files = new RuntimeFileServiceAdapter(runtimeFiles);
        RuntimeTaskServiceAdapter tasks = new RuntimeTaskServiceAdapter(
            new InMemoryTaskStore(),
            runtimeFiles,
            new RuntimeSubjectContextMapper(),
            Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        SubjectContext subject = subject("u-1");
        FileRef resultFile = files.save(FileStreamWriteRequest.builder()
            .fileName("students.csv")
            .contentType("text/csv")
            .inputStream(new ByteArrayInputStream("id,name".getBytes(StandardCharsets.UTF_8)))
            .size(Long.valueOf(7L))
            .attributes(fileAttributes(subject, "EXPORT_RESULT", "csv"))
            .build());
        ExportSpec spec = ExportSpec.builder()
            .scene("student.export")
            .rootType(SampleEntity.class)
            .operation(ExportOperation.SUBMIT)
            .subject(subject)
            .build();
        CrudTask task = tasks.create(CrudTask.builder()
            .status(CrudTaskStatus.PENDING)
            .contextSnapshot(CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey()))
            .resultFile(resultFile)
            .message("等待导出")
            .build());

        CrudTask running = tasks.updateStatus(task.getTaskId(), CrudTaskStatus.RUNNING, "正在导出");
        CrudTask loaded = tasks.updateStatus(running.getTaskId(), CrudTaskStatus.SUCCEEDED, "导出完成");
        new TaskFileAccessGuard().assertTaskAccessible(loaded, spec);
        new TaskFileAccessGuard().assertDownloadableFile(loaded.getResultFile(), "EXPORT_RESULT");

        assertEquals(task.getTaskId(), loaded.getTaskId());
        assertEquals("id,name", new String(files.read(loaded.getResultFile()), StandardCharsets.UTF_8));
        assertThrows(RuntimeException.class, () -> tasks.cancel(loaded.getTaskId(), "不应覆盖成功任务"));
        assertThrows(RuntimeException.class, () -> tasks.create(CrudTask.builder()
            .status(CrudTaskStatus.SUCCEEDED)
            .contextSnapshot(CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey()))
            .build()));
        assertThrows(RuntimeException.class, () -> new TaskFileAccessGuard().assertTaskAccessible(
            loaded,
            spec.toBuilder().subject(subject("other-user")).build()
        ));

        FileRef foreignFile = files.save(FileStreamWriteRequest.builder()
            .fileName("foreign.csv")
            .contentType("text/csv")
            .inputStream(new ByteArrayInputStream(new byte[] {1}))
            .size(Long.valueOf(1L))
            .attributes(fileAttributes(subject("other-user"), "EXPORT_RESULT", "csv"))
            .build());
        CrudException ownership = assertThrows(CrudException.class, () -> tasks.create(CrudTask.builder()
            .status(CrudTaskStatus.PENDING)
            .contextSnapshot(CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey()))
            .resultFile(foreignFile)
            .build()));
        assertEquals(CrudErrorCode.PERMISSION_DENIED, ownership.getErrorCode());

        CrudException invalidTaskId = assertThrows(CrudException.class,
            () -> tasks.getRequired(" "));
        assertEquals(CrudErrorCode.VALIDATION_ERROR, invalidTaskId.getErrorCode());
    }

    @Test
    void runtimeAdapterAllowsSuccessfulImportWithoutResultFile() {
        InMemoryFileStore runtimeFiles = new InMemoryFileStore();
        RuntimeTaskServiceAdapter tasks = new RuntimeTaskServiceAdapter(
            new InMemoryTaskStore(),
            runtimeFiles,
            new RuntimeSubjectContextMapper(),
            Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        SubjectContext subject = subject("u-1");
        CrudTask task = tasks.create(CrudTask.builder()
            .status(CrudTaskStatus.PENDING)
            .contextSnapshot(CrudTaskContextSnapshot.builder()
                .scene("student.import")
                .rootType(SampleEntity.class)
                .operationKey(CrudOperationKey.of(CrudOperationDomain.IMPORT, "SUBMIT"))
                .subject(subject)
                .build())
            .message("等待导入")
            .build());

        CrudTask running = tasks.updateStatus(task.getTaskId(), CrudTaskStatus.RUNNING, "正在导入");
        CrudTask succeeded = tasks.updateStatus(running.getTaskId(), CrudTaskStatus.SUCCEEDED, "导入完成");

        assertEquals(CrudTaskStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals(null, succeeded.getResultFile());
    }

    @Test
    void runtimeAdapterRejectsSuccessfulExportWithoutResultFile() {
        RuntimeTaskServiceAdapter tasks = new RuntimeTaskServiceAdapter(
            new InMemoryTaskStore(),
            new InMemoryFileStore(),
            new RuntimeSubjectContextMapper(),
            Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)
        );
        SubjectContext subject = subject("u-1");
        CrudTask task = tasks.create(CrudTask.builder()
            .status(CrudTaskStatus.PENDING)
            .contextSnapshot(CrudTaskContextSnapshot.builder()
                .scene("student.export")
                .rootType(SampleEntity.class)
                .operationKey(CrudOperationKey.of(CrudOperationDomain.EXPORT, "SUBMIT"))
                .subject(subject)
                .build())
            .build());
        CrudTask running = tasks.updateStatus(task.getTaskId(), CrudTaskStatus.RUNNING, "正在导出");

        CrudException exception = assertThrows(CrudException.class,
            () -> tasks.updateStatus(running.getTaskId(), CrudTaskStatus.SUCCEEDED, "导出完成"));

        assertEquals(CrudErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    private static SubjectContext subject(String subjectId) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(subjectId);
        subject.setTenantId("tenant-1");
        subject.setOrgId("org-1");
        return subject;
    }

    private static Map<String, Object> fileAttributes(SubjectContext subject, String purpose, String format) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("purpose", purpose);
        attributes.put("format", format);
        attributes.put("subjectId", subject.getSubjectId());
        attributes.put("tenantId", subject.getTenantId());
        attributes.put("orgId", subject.getOrgId());
        return attributes;
    }

    private static final class SampleEntity {
    }
}
