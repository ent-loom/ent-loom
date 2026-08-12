package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.runtime.spec.CrudSpecAttributeKeys;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultCrudDataScopeResolverTest {
    private final DefaultCrudDataScopeResolver resolver = new DefaultCrudDataScopeResolver();

    @Test
    void should_prefer_explicit_scope_object_from_attributes() {
        CrudDataScope scope = CrudDataScope.scoped(dimensions("tenantId", "tenant-from-attr"));
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .attributes(singletonAttribute(CrudSpecAttributeKeys.CRUD_DATA_SCOPE, scope))
            .build();

        CrudDataScope resolved = resolver.resolveQueryScope(null, subject("tenant-subject", "org-subject"), spec);

        Assertions.assertSame(scope, resolved);
    }

    @Test
    void should_resolve_explicit_all_from_attributes() {
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .attributes(singletonAttribute(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL, Boolean.TRUE))
            .build();

        CrudDataScope resolved = resolver.resolveQueryScope(null, subject("tenant-subject", "org-subject"), spec);

        Assertions.assertTrue(resolved.isExplicitAll());
        Assertions.assertTrue(resolved.getDimensions().isEmpty());
    }

    @Test
    void should_resolve_dimension_map_from_attributes() {
        Map<String, Object> dimensions = dimensions("orgId", "org-from-attr");
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .attributes(singletonAttribute(CrudSpecAttributeKeys.CRUD_DATA_SCOPE_DIMENSIONS, dimensions))
            .build();

        CrudDataScope resolved = resolver.resolveQueryScope(null, subject("tenant-subject", "org-subject"), spec);

        Assertions.assertFalse(resolved.isExplicitAll());
        Assertions.assertEquals("org-from-attr", resolved.getDimensions().get("orgId"));
    }

    @Test
    void should_fallback_to_org_then_tenant_from_subject() {
        CrudDataScope orgScope = resolver.resolveQueryScope(null, subject("tenant-a", "org-a"), QuerySpec.<Object>builder().build());
        CrudDataScope tenantScope = resolver.resolveQueryScope(null, subject("tenant-a", null), QuerySpec.<Object>builder().build());

        Assertions.assertEquals("org-a", orgScope.getDimensions().get("orgId"));
        Assertions.assertFalse(orgScope.getDimensions().containsKey("tenantId"));
        Assertions.assertEquals("tenant-a", tenantScope.getDimensions().get("tenantId"));
    }

    @Test
    void should_return_null_when_no_attribute_or_subject_scope_available() {
        Assertions.assertNull(resolver.resolveQueryScope(null, new SubjectContext(), QuerySpec.<Object>builder().build()));
    }

    private SubjectContext subject(String tenantId, String orgId) {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId(tenantId);
        subject.setOrgId(orgId);
        return subject;
    }

    private Map<String, Object> singletonAttribute(String key, Object value) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put(key, value);
        return attributes;
    }

    private Map<String, Object> dimensions(String key, Object value) {
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put(key, value);
        return dimensions;
    }
}
