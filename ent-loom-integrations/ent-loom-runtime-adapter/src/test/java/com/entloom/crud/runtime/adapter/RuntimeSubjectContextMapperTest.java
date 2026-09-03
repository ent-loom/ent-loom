package com.entloom.crud.runtime.adapter;

import com.entloom.crud.api.model.SubjectContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeSubjectContextMapperTest {
    @Test
    void mapsSharedIdentityFieldsAndUsesConfiguredType() {
        SubjectContext source = new SubjectContext();
        source.setSubjectId("u-1");
        source.setTenantId("tenant-1");
        source.setOrgId("org-1");

        RuntimeSubjectContextMapper mapper = new RuntimeSubjectContextMapper("operator");
        com.entloom.runtime.contract.context.SubjectContext runtime = mapper.toRuntime(source);

        assertEquals("u-1", runtime.getSubjectId());
        assertEquals("operator", runtime.getSubjectType());
        assertEquals("tenant-1", runtime.getTenantId());
        assertEquals("org-1", runtime.getOrgId());
        assertEquals("tenant-1", mapper.toCrud(runtime).getTenantId());
    }

    @Test
    void rejectsEmptySubject() {
        SubjectContext source = new SubjectContext();
        assertThrows(IllegalArgumentException.class, () -> new RuntimeSubjectContextMapper().toRuntime(source));
    }
}
