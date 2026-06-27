package com.entloom.crud.core.foundation.taskfile;

import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.exception.CrudException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TaskFileContractTest {
    @Test
    void snapshot_should_copy_subject_and_attributes_from_spec() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("requestId", "REQ-1");
        ImportSpec spec = ImportSpec.builder()
            .scene("student.import")
            .rootType(Object.class)
            .operation(ImportOperation.SUBMIT)
            .subject(subject)
            .attributes(attributes)
            .build();

        CrudTaskContextSnapshot snapshot = CrudTaskContextSnapshot.fromSpec(spec, spec.getOperationKey());
        subject.setSubjectId("changed");
        attributes.put("requestId", "REQ-2");

        Assertions.assertEquals("student.import", snapshot.getScene());
        Assertions.assertEquals("tester", snapshot.getSubject().getSubjectId());
        Assertions.assertEquals("REQ-1", snapshot.getAttributes().get("requestId"));
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
        LocalTaskService taskService = new LocalTaskService(tempDir.resolve("tasks").toString());
        CrudTask created = taskService.create(CrudTask.builder()
            .status(CrudTaskStatus.SUCCEEDED)
            .resultFile(file)
            .progress(Integer.valueOf(100))
            .build());

        LocalFileService reloadedFileService = new LocalFileService(tempDir.resolve("files").toString());
        LocalTaskService reloadedTaskService = new LocalTaskService(tempDir.resolve("tasks").toString());

        Assertions.assertEquals("ok", new String(reloadedFileService.read(file), StandardCharsets.UTF_8));
        Assertions.assertEquals(file.getFileId(), reloadedTaskService.getRequired(created.getTaskId()).getResultFile().getFileId());
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

    private static Map<String, Object> singletonAttribute(String key, Object value) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(key, value);
        return attributes;
    }
}
