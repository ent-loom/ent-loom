package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TaskFileContractTest {
    @Test
    void snapshot_should_copy_subject_and_attributes_from_spec() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("requestId", "REQ-1");
        attributes.put("traceId", "TRACE-1");
        Map<String, Object> dimensions = new HashMap<String, Object>();
        dimensions.put("tenantId", "tenant-a");
        ImportSpec spec = ImportSpec.builder()
            .scene("student.import")
            .rootType(Object.class)
            .operation(ImportOperation.SUBMIT)
            .subject(subject)
            .attributes(attributes)
            .grantedScope(CrudDataScope.scoped(dimensions))
            .governanceScope(CrudDataScope.scoped(dimensions))
            .build();

        CrudTaskContextSnapshot snapshot = CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey());
        subject.setSubjectId("changed");
        attributes.put("requestId", "REQ-2");

        Assertions.assertEquals("student.import", snapshot.getScene());
        Assertions.assertEquals("tester", snapshot.getSubject().getSubjectId());
        Assertions.assertEquals("REQ-1", snapshot.getAttributes().get("requestId"));
        Assertions.assertEquals("REQ-1", snapshot.getAuditContext().get("requestId"));
        Assertions.assertEquals("TRACE-1", snapshot.getAuditContext().get("traceId"));
        Assertions.assertEquals("tenant-a", snapshot.getGrantedScope().getDimensions().get("tenantId"));
        Assertions.assertEquals("tenant-a", snapshot.getGovernanceScope().getDimensions().get("tenantId"));
    }

    @Test
    void file_write_request_should_copy_content() {
        byte[] content = new byte[] {1, 2};
        FileWriteRequest request = FileWriteRequest.builder().fileName("data.bin").content(content).build();
        content[0] = 9;

        Assertions.assertEquals(1, request.getContent()[0]);
        byte[] exposed = request.getContent();
        exposed[0] = 8;
        Assertions.assertEquals(1, request.getContent()[0]);
    }

    @Test
    void local_services_should_persist_file_and_task_metadata() throws IOException {
        Path tempDir = Files.createTempDirectory("entloom-crud-taskfile-test");
        LocalFileService fileService = new LocalFileService(tempDir.resolve("files").toString());
        FileRef file = fileService.save(FileWriteRequest.builder()
            .fileName("result.txt")
            .contentType("text/plain")
            .content("ok".getBytes(StandardCharsets.UTF_8))
            .build());
        Map<String, Object> fileAttributes = new HashMap<String, Object>();
        fileAttributes.put("purpose", "EXPORT_RESULT");
        fileAttributes.put("attempt", Integer.valueOf(2));
        FileRef taskFile = FileRef.builder()
            .fileId(file.getFileId())
            .fileName(file.getFileName())
            .contentType(file.getContentType())
            .size(file.getSize())
            .attributes(fileAttributes)
            .build();
        Map<String, Object> contextAttributes = new HashMap<String, Object>();
        contextAttributes.put("requestId", "REQ-LOCAL");
        contextAttributes.put("attempt", Integer.valueOf(3));
        contextAttributes.put("enabled", Boolean.TRUE);
        contextAttributes.put("startedAt", Instant.parse("2026-05-02T00:00:00Z"));
        contextAttributes.put("value.original", "VALUE");
        contextAttributes.put("type.original", "TYPE");
        LocalTaskService taskService = new LocalTaskService(tempDir.resolve("tasks").toString());
        CrudTask created = taskService.create(CrudTask.builder()
            .status(CrudTaskStatus.SUCCEEDED)
            .contextSnapshot(CrudTaskContextSnapshot.builder()
                .subject(subjectWith("tester", "tenant-a", "org-a"))
                .grantedScope(CrudDataScope.scoped(singletonAttribute("tenantId", "tenant-a")))
                .governanceScope(CrudDataScope.scoped(singletonAttribute("orgId", "org-a")))
                .auditContext(singletonAttribute("traceId", "TRACE-LOCAL"))
                .attributes(contextAttributes)
                .build())
            .resultFile(taskFile)
            .progress(Integer.valueOf(100))
            .build());

        LocalFileService reloadedFileService = new LocalFileService(tempDir.resolve("files").toString());
        LocalTaskService reloadedTaskService = new LocalTaskService(tempDir.resolve("tasks").toString());

        Assertions.assertEquals("ok", new String(reloadedFileService.read(file), StandardCharsets.UTF_8));
        CrudTask reloaded = reloadedTaskService.getRequired(created.getTaskId());
        Assertions.assertEquals(file.getFileId(), reloaded.getResultFile().getFileId());
        Assertions.assertEquals(Integer.valueOf(2), reloaded.getResultFile().getAttributes().get("attempt"));
        Assertions.assertEquals("tenant-a", reloaded.getContextSnapshot().getGrantedScope().getDimensions().get("tenantId"));
        Assertions.assertEquals("org-a", reloaded.getContextSnapshot().getGovernanceScope().getDimensions().get("orgId"));
        Assertions.assertEquals("TRACE-LOCAL", reloaded.getContextSnapshot().getAuditContext().get("traceId"));
        Assertions.assertEquals("REQ-LOCAL", reloaded.getContextSnapshot().getAttributes().get("requestId"));
        Assertions.assertEquals(Integer.valueOf(3), reloaded.getContextSnapshot().getAttributes().get("attempt"));
        Assertions.assertEquals(Boolean.TRUE, reloaded.getContextSnapshot().getAttributes().get("enabled"));
        Assertions.assertEquals(
            Instant.parse("2026-05-02T00:00:00Z"),
            reloaded.getContextSnapshot().getAttributes().get("startedAt")
        );
        Assertions.assertEquals("VALUE", reloaded.getContextSnapshot().getAttributes().get("value.original"));
        Assertions.assertEquals("TYPE", reloaded.getContextSnapshot().getAttributes().get("type.original"));
    }

    @Test
    void local_task_service_should_preserve_legacy_attribute_names_with_reserved_prefixes() throws IOException {
        Path tempDir = Files.createTempDirectory("entloom-crud-legacy-task-attribute-test");
        Path taskDir = tempDir.resolve("tasks");
        Files.createDirectories(taskDir);
        Properties properties = new Properties();
        properties.setProperty("taskId", "legacy");
        properties.setProperty("status", CrudTaskStatus.PENDING.name());
        properties.setProperty("context.audit.value.requestId", "legacy-value");
        properties.setProperty("context.audit.type.traceId", "legacy-type");
        try (OutputStream output = Files.newOutputStream(taskDir.resolve("legacy.properties"))) {
            properties.store(output, "legacy task metadata");
        }

        CrudTaskContextSnapshot snapshot = new LocalTaskService(taskDir.toString())
            .getRequired("legacy")
            .getContextSnapshot();

        Assertions.assertEquals("legacy-value", snapshot.getAuditContext().get("value.requestId"));
        Assertions.assertEquals("legacy-type", snapshot.getAuditContext().get("type.traceId"));
    }

    @Test
    void local_task_service_should_reject_unsupported_attribute_type() throws IOException {
        Path tempDir = Files.createTempDirectory("entloom-crud-task-attribute-test");
        LocalTaskService taskService = new LocalTaskService(tempDir.resolve("tasks").toString());

        Assertions.assertThrows(
            ValidationException.class,
            () -> taskService.create(CrudTask.builder()
                .contextSnapshot(CrudTaskContextSnapshot.builder()
                    .attributes(singletonAttribute("unsupported", new Object()))
                    .build())
                .build())
        );
    }

    @Test
    void local_task_service_should_reject_path_traversal_task_id() throws IOException {
        Path tempDir = Files.createTempDirectory("entloom-crud-task-id-test");
        LocalTaskService taskService = new LocalTaskService(tempDir.resolve("tasks").toString());

        Assertions.assertThrows(
            ValidationException.class,
            () -> taskService.create(CrudTask.builder().taskId("../outside").build())
        );
        Assertions.assertFalse(Files.exists(tempDir.resolve("outside.properties")));
    }

    @Test
    void local_file_service_should_save_and_open_stream() throws IOException {
        Path tempDir = Files.createTempDirectory("entloom-crud-streamfile-test");
        LocalFileService fileService = new LocalFileService(tempDir.resolve("files").toString());

        FileRef file = fileService.save(FileStreamWriteRequest.builder()
            .fileName("stream.txt")
            .contentType("text/plain")
            .inputStream(new ByteArrayInputStream("stream-ok".getBytes(StandardCharsets.UTF_8)))
            .size(Long.valueOf(9L))
            .build());

        byte[] content = copyToByteArray(fileService.openStream(file));
        Assertions.assertEquals("stream-ok", new String(content, StandardCharsets.UTF_8));
    }

    @Test
    void access_guard_should_reject_different_subject() {
        SubjectContext owner = new SubjectContext();
        owner.setSubjectId("u1");
        SubjectContext current = new SubjectContext();
        current.setSubjectId("u2");
        ImportSpec ownerSpec = ImportSpec.builder()
            .scene("student.import")
            .rootType(Object.class)
            .operation(ImportOperation.SUBMIT)
            .subject(owner)
            .build();
        ImportSpec currentSpec = ownerSpec.toBuilder().subject(current).build();
        CrudTask task = CrudTask.builder()
            .taskId("T1")
            .contextSnapshot(CrudTaskContextSnapshot.fromSpec(ownerSpec, ownerSpec.getOperationKey()))
            .build();

        Assertions.assertThrows(CrudException.class, () -> new TaskFileAccessGuard().assertTaskAccessible(task, currentSpec));
    }

    @Test
    void access_guard_should_reject_download_file_with_missing_metadata() {
        FileRef file = FileRef.builder()
            .fileId("F1")
            .fileName("result.xlsx")
            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .size(Long.valueOf(12L))
            .attributes(singletonAttribute("purpose", "EXPORT_RESULT"))
            .build();

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> new TaskFileAccessGuard().assertDownloadableFile(file, "EXPORT_RESULT")
        );

        Assertions.assertEquals(com.entloom.crud.api.enums.CrudErrorCode.FILE_METADATA_INVALID, ex.getErrorCode());
    }

    @Test
    void access_guard_should_reject_import_source_from_another_tenant() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("u1");
        subject.setTenantId("tenant-a");
        ImportSpec spec = ImportSpec.builder().rootType(Object.class).subject(subject).build();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("purpose", "IMPORT_SOURCE");
        attributes.put("subjectId", "u1");
        attributes.put("tenantId", "tenant-b");
        FileRef file = FileRef.builder().fileId("F1").attributes(attributes).build();

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> new TaskFileAccessGuard().assertImportSourceFileAccessible(file, spec)
        );

        Assertions.assertEquals(com.entloom.crud.api.enums.CrudErrorCode.PERMISSION_DENIED, ex.getErrorCode());
    }

    private static Map<String, Object> singletonAttribute(String key, Object value) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(key, value);
        return attributes;
    }

    private static SubjectContext subjectWith(String subjectId, String tenantId, String orgId) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId(subjectId);
        subject.setTenantId(tenantId);
        subject.setOrgId(orgId);
        return subject;
    }

    private static byte[] copyToByteArray(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InputStream in = inputStream) {
            int len;
            while ((len = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        }
        return outputStream.toByteArray();
    }
}
