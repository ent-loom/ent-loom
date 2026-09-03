package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.foundation.taskfile.CrudTask;
import com.entloom.crud.core.foundation.taskfile.CrudTaskContextSnapshot;
import com.entloom.crud.core.foundation.taskfile.CrudTaskStatus;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrudRuntimeTaskMapperTest {
    @Test
    void mapsTaskStatusContextFilesAndErrorFileReference() {
        SubjectContext subject = subject();
        FileRef result = file("result-1", "EXPORT_RESULT");
        FileRef error = file("error-1", "IMPORT_ERROR");
        CrudTask source = CrudTask.builder()
            .taskId("task-1")
            .status(CrudTaskStatus.SUCCEEDED)
            .contextSnapshot(CrudTaskContextSnapshot.builder()
                .scene("student.export")
                .rootType(SampleEntity.class)
                .operationKey(CrudOperationKey.of(CrudOperationDomain.EXPORT, "SUBMIT"))
                .subject(subject)
                .grantedScope(CrudDataScope.scoped(Collections.<String, Object>singletonMap("tenantId", "tenant-1")))
                .auditContext(Collections.<String, Object>singletonMap("requestId", "request-1"))
                .attributes(Collections.<String, Object>singletonMap("traceId", "trace-1"))
                .build())
            .resultFile(result)
            .errorFile(error)
            .progress(Integer.valueOf(100))
            .message("完成")
            .createdAt(Instant.parse("2026-09-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-09-01T00:01:00Z"))
            .finishedAt(Instant.parse("2026-09-01T00:01:00Z"))
            .build();

        CrudRuntimeTaskMapper mapper = new CrudRuntimeTaskMapper();
        com.entloom.runtime.contract.task.Task runtime = mapper.toRuntime(source);
        CrudTask restored = mapper.toCrud(runtime, fileId -> error);

        assertEquals("EXPORT", runtime.getTaskType());
        assertEquals(com.entloom.runtime.contract.task.TaskStatus.SUCCEEDED, runtime.getStatus());
        assertEquals("error-1", runtime.getAttributes().get(CrudRuntimeTaskMapper.ERROR_FILE_ID_ATTRIBUTE));
        assertEquals(SampleEntity.class, restored.getContextSnapshot().getRootType());
        assertEquals("student.export", restored.getContextSnapshot().getScene());
        assertEquals("tenant-1", restored.getContextSnapshot().getGrantedScope().getDimensions().get("tenantId"));
        assertEquals("request-1", restored.getContextSnapshot().getAuditContext().get("requestId"));
        assertEquals("trace-1", restored.getContextSnapshot().getAttributes().get("traceId"));
        assertEquals("error-1", restored.getErrorFile().getFileId());
        assertEquals("result-1", restored.getResultFile().getFileId());
    }

    @Test
    void doesNotPretendExpiredIsSupportedByRuntime() {
        CrudTask source = CrudTask.builder()
            .status(CrudTaskStatus.EXPIRED)
            .contextSnapshot(CrudTaskContextSnapshot.builder()
                .rootType(SampleEntity.class)
                .operationKey(CrudOperationKey.of(CrudOperationDomain.EXPORT, "SUBMIT"))
                .subject(subject())
                .build())
            .taskId("task-expired")
            .build();

        assertThrows(IllegalArgumentException.class, () -> new CrudRuntimeTaskMapper().toRuntime(source));
    }

    private static SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("u-1");
        subject.setTenantId("tenant-1");
        subject.setOrgId("org-1");
        return subject;
    }

    private static FileRef file(String fileId, String purpose) {
        java.util.Map<String, Object> attributes = new java.util.HashMap<String, Object>();
        attributes.put("purpose", purpose);
        attributes.put("format", "csv");
        attributes.put("subjectId", "u-1");
        attributes.put("tenantId", "tenant-1");
        attributes.put("orgId", "org-1");
        return FileRef.builder()
            .fileId(fileId)
            .fileName(fileId + ".csv")
            .contentType("text/csv")
            .size(Long.valueOf(2L))
            .storageKey("files/" + fileId)
            .attributes(attributes)
            .build();
    }

    private static final class SampleEntity {
    }
}
