package com.entloom.crud.core.runtime.spec;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultCrudSpecAttributeResolverTest {
    @Test
    void should_strip_client_reserved_governance_keys_before_contributor_merge() {
        Map<String, Object> clientAttributes = new LinkedHashMap<String, Object>();
        clientAttributes.put("requestId", "REQ-1");
        clientAttributes.put(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL, Boolean.TRUE);
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(Object.class)
            .op(QueryOperation.PAGE)
            .subject(subject())
            .attributes(clientAttributes)
            .build();
        DefaultCrudSpecAttributeResolver resolver = new DefaultCrudSpecAttributeResolver(Collections.singletonList(current -> {
            Map<String, Object> contributed = new LinkedHashMap<String, Object>();
            contributed.put(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL, Boolean.TRUE);
            contributed.put("traceId", "TRACE-1");
            return contributed;
        }));

        Map<String, Object> resolved = resolver.resolve(spec);

        Assertions.assertEquals("REQ-1", resolved.get("requestId"));
        Assertions.assertEquals("TRACE-1", resolved.get("traceId"));
        Assertions.assertEquals(Boolean.TRUE, resolved.get(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL));
    }

    @Test
    void should_strip_additional_reserved_keys_from_client_attributes() {
        Map<String, Object> clientAttributes = new LinkedHashMap<String, Object>();
        clientAttributes.put("serverOnly", "forged");
        clientAttributes.put("requestId", "REQ-1");
        DefaultCrudSpecAttributeResolver resolver = new DefaultCrudSpecAttributeResolver(null, Collections.singleton("serverOnly"));

        Map<String, Object> resolved = resolver.resolve(QuerySpec.<Object>builder().attributes(clientAttributes).build());

        Assertions.assertFalse(resolved.containsKey("serverOnly"));
        Assertions.assertEquals("REQ-1", resolved.get("requestId"));
    }

    @Test
    void should_reject_invalid_contributed_reserved_values() {
        DefaultCrudSpecAttributeResolver resolver = new DefaultCrudSpecAttributeResolver(Collections.singletonList(current ->
            Collections.singletonMap(CrudSpecAttributeKeys.CRUD_EXPLICIT_ALL, Boolean.FALSE)
        ));

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> resolver.resolve(QuerySpec.<Object>builder().build())
        );

        Assertions.assertTrue(ex.getMessage().contains("crudExplicitAll"));
    }

    @Test
    void should_reject_blank_or_control_character_keys() {
        DefaultCrudSpecAttributeResolver resolver = new DefaultCrudSpecAttributeResolver(Collections.singletonList(current ->
            Collections.singletonMap("bad\nkey", "value")
        ));

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> resolver.resolve(QuerySpec.<Object>builder().build())
        );

        Assertions.assertTrue(ex.getMessage().contains("控制字符"));
    }

    @Test
    void should_accept_trusted_data_scope_dimensions() {
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put("orgId", Arrays.asList("org-a", "org-b"));
        DefaultCrudSpecAttributeResolver resolver = new DefaultCrudSpecAttributeResolver(Collections.singletonList(current -> {
            Map<String, Object> contributed = new LinkedHashMap<String, Object>();
            contributed.put(CrudSpecAttributeKeys.CRUD_DATA_SCOPE_DIMENSIONS, dimensions);
            contributed.put(CrudSpecAttributeKeys.CRUD_DATA_SCOPE, CrudDataScope.scoped(dimensions));
            return contributed;
        }));

        Map<String, Object> resolved = resolver.resolve(QuerySpec.<Object>builder().build());

        Assertions.assertSame(dimensions, resolved.get(CrudSpecAttributeKeys.CRUD_DATA_SCOPE_DIMENSIONS));
        Assertions.assertTrue(resolved.get(CrudSpecAttributeKeys.CRUD_DATA_SCOPE) instanceof CrudDataScope);
    }

    private SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        return subject;
    }
}
