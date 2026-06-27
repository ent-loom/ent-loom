package com.entloom.crud.core.capability.importing;

import com.entloom.crud.api.enums.CrudOperationDomain;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.core.foundation.taskfile.FileRef;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImportSpecTest {
    @Test
    void should_build_import_operation_key_and_copy_payload() {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("businessKey", "studentNo");
        ImportSpec spec = ImportSpec.builder()
            .rootType(Object.class)
            .operation(ImportOperation.VALIDATE)
            .format("generic-tabular")
            .sourceFile(FileRef.builder().fileId("file-1").build())
            .payload(payload)
            .build();
        payload.put("businessKey", "changed");

        Assertions.assertEquals(CrudOperationDomain.IMPORT, spec.getOperationKey().getDomain());
        Assertions.assertEquals("VALIDATE", spec.getOperationKey().getOperation());
        Assertions.assertEquals("studentNo", spec.getPayload().get("businessKey"));

        Map<String, Object> exposed = spec.getPayload();
        exposed.put("businessKey", "mutated");
        Assertions.assertEquals("studentNo", spec.getPayload().get("businessKey"));
    }
}
