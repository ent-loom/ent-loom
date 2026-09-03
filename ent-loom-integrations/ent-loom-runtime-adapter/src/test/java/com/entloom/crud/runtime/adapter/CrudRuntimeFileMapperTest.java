package com.entloom.crud.runtime.adapter;

import com.entloom.crud.core.foundation.taskfile.CrudFileStorageType;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.foundation.taskfile.FileStreamWriteRequest;
import com.entloom.crud.core.foundation.taskfile.FileWriteRequest;
import com.entloom.runtime.inmemory.file.InMemoryFileStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CrudRuntimeFileMapperTest {
    @Test
    void mapsWriteRequestsAndPromotesCrudMetadata() throws Exception {
        Map<String, Object> attributes = attributes("EXPORT_RESULT", "u-1", "tenant-1", "org-1");
        attributes.put("format", "csv");
        attributes.put("storageType", "LOCAL");
        FileWriteRequest source = FileWriteRequest.builder()
            .fileName("result.csv")
            .contentType("text/csv")
            .content("ok".getBytes("UTF-8"))
            .attributes(attributes)
            .build();

        CrudRuntimeFileMapper mapper = new CrudRuntimeFileMapper();
        com.entloom.runtime.contract.file.FileWriteRequest runtime = mapper.toRuntime(source);

        assertEquals("EXPORT_RESULT", runtime.getPurpose());
        assertEquals("u-1", runtime.getOwner().getSubjectId());
        assertEquals("LOCAL", runtime.getAttributes().get("storageType"));
        assertArrayEquals("ok".getBytes("UTF-8"), runtime.getContent());
    }

    @Test
    void mapsStreamRequestAndFileReferenceBackWithoutLosingOwnership() throws Exception {
        Map<String, Object> attributes = attributes("EXPORT_RESULT", "u-1", "tenant-1", "org-1");
        attributes.put("format", "csv");
        attributes.put("storageType", "OBJECT_STORAGE");
        FileStreamWriteRequest source = FileStreamWriteRequest.builder()
            .fileName("result.csv")
            .contentType("text/csv")
            .inputStream(new ByteArrayInputStream("ok".getBytes("UTF-8")))
            .size(Long.valueOf(2L))
            .attributes(attributes)
            .build();

        CrudRuntimeFileMapper mapper = new CrudRuntimeFileMapper();
        com.entloom.runtime.contract.file.FileStreamWriteRequest runtime = mapper.toRuntime(source);
        com.entloom.runtime.contract.file.FileRef runtimeRef = com.entloom.runtime.contract.file.FileRef.builder()
            .fileId("file-1")
            .fileName(runtime.getFileName())
            .contentType(runtime.getContentType())
            .size(2L)
            .storageKey("objects/file-1")
            .purpose(runtime.getPurpose())
            .owner(runtime.getOwner())
            .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
            .attributes(runtime.getAttributes())
            .build();

        FileRef crud = mapper.toCrud(runtimeRef);
        assertEquals("OBJECT_STORAGE", crud.getStorageType().name());
        assertEquals("EXPORT_RESULT", crud.getAttributes().get("purpose"));
        assertEquals("u-1", crud.getAttributes().get("subjectId"));
        assertEquals("csv", crud.getAttributes().get("format"));
        assertEquals(Instant.parse("2030-01-01T00:00:00Z"), crud.getExpiresAt());
    }

    @Test
    void fileAdapterContractUsesRuntimeMetadataForReadAndStream() throws Exception {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        InMemoryFileStore runtimeStore = new InMemoryFileStore(Clock.fixed(now, ZoneOffset.UTC));
        RuntimeFileServiceAdapter adapter = new RuntimeFileServiceAdapter(runtimeStore);
        Map<String, Object> attributes = attributes("EXPORT_RESULT", "u-1", "tenant-1", "org-1");
        attributes.put("format", "csv");

        FileRef file = adapter.save(FileStreamWriteRequest.builder()
            .fileName("result.csv")
            .contentType("text/csv")
            .inputStream(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)))
            .size(Long.valueOf(2L))
            .attributes(attributes)
            .build());

        assertArrayEquals("ok".getBytes(StandardCharsets.UTF_8), adapter.read(file));
        try (InputStream inputStream = adapter.openStream(file)) {
            assertArrayEquals("ok".getBytes(StandardCharsets.UTF_8), inputStream.readAllBytes());
        }
        assertEquals(file.getFileId(), adapter.getRequired(file.getFileId()).getFileId());
    }

    private static Map<String, Object> attributes(String purpose, String subjectId, String tenantId, String orgId) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("purpose", purpose);
        attributes.put("subjectId", subjectId);
        attributes.put("tenantId", tenantId);
        attributes.put("orgId", orgId);
        return attributes;
    }
}
