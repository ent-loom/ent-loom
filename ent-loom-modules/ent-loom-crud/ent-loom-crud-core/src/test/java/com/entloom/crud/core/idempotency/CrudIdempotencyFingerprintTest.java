package com.entloom.crud.core.idempotency;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportMode;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudIdempotencyFingerprintTest {
    @Test
    void exportFingerprintShouldOnlyContainCanonicalizableValues() {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("when", Instant.parse("2026-09-01T00:00:00Z"));
        ExportSpec spec = ExportSpec.builder()
            .rootType(SampleEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(SampleEntity.class))
            .filters(Collections.singletonList(
                new QueryFilter("createdAt", FilterOperator.GE, Instant.parse("2026-09-01T00:00:00Z"))
            ))
            .payload(payload)
            .grantedScope(CrudDataScope.scoped(Collections.<String, Object>singletonMap(
                "tenantId", "tenant-a")))
            .build();

        Map<String, Object> fingerprint = CrudIdempotencyFingerprint.forExport(spec);

        Assertions.assertDoesNotThrow(() -> new StablePayloadCanonicalizer().canonicalize(fingerprint));
        Assertions.assertTrue(((String) fingerprint.get("rootType")).endsWith("$SampleEntity"));
    }

    @Test
    void importFingerprintShouldUseSourceFileIdAndRetainTypedRequestSemantics() {
        ImportSpec first = ImportSpec.builder()
            .rootType(SampleEntity.class)
            .mode(ImportMode.UPSERT)
            .sourceFile(FileRef.builder().fileId("file-1").build())
            .payload(Collections.<String, Object>singletonMap("active", Boolean.TRUE))
            .build();
        ImportSpec second = first.toBuilder()
            .sourceFile(FileRef.builder().fileId("file-1").fileName("renamed.csv").build())
            .build();

        String firstCanonical = new StablePayloadCanonicalizer().canonicalize(
            CrudIdempotencyFingerprint.forImport(first));
        String secondCanonical = new StablePayloadCanonicalizer().canonicalize(
            CrudIdempotencyFingerprint.forImport(second));

        Assertions.assertEquals(firstCanonical, secondCanonical);
    }

    private static final class SampleEntity {
    }
}
