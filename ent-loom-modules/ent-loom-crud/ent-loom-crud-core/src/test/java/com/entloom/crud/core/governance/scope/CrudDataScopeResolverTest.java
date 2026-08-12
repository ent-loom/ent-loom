package com.entloom.crud.core.governance.scope;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.exporting.ExportSpec;
import com.entloom.crud.core.capability.importing.ImportSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudDataScopeResolverTest {
    @Test
    void default_import_scope_should_bridge_common_spec_context_to_command_scope() {
        CapturingResolver resolver = new CapturingResolver();
        SubjectContext subject = subject();
        Map<String, Object> attributes = singleton("scopeKey", "scopeValue");
        Map<String, Object> payload = singleton("payloadKey", "payloadValue");
        ImportSpec spec = ImportSpec.builder()
            .rootType(String.class)
            .entityClasses(Collections.<Class<?>>singletonList(Integer.class))
            .scene("import-scene")
            .attributes(attributes)
            .payload(payload)
            .build();

        resolver.resolveImportScope(null, subject, spec);

        CommandSpec<?> bridged = resolver.commandSpec;
        Assertions.assertNotNull(bridged);
        Assertions.assertEquals(String.class, bridged.getRootType());
        Assertions.assertEquals(Integer.class, bridged.getEntityClasses().get(0));
        Assertions.assertEquals("import-scene", bridged.getScene());
        Assertions.assertEquals(subject.getSubjectId(), bridged.getSubject().getSubjectId());
        Assertions.assertEquals("scopeValue", bridged.getAttributes().get("scopeKey"));
        Assertions.assertEquals("payloadValue", ((Map<?, ?>) bridged.getPayload()).get("payloadKey"));
    }

    @Test
    void default_export_scope_should_bridge_filterable_context_to_query_scope() {
        CapturingResolver resolver = new CapturingResolver();
        SubjectContext subject = subject();
        QueryFilter filter = new QueryFilter("status", FilterOperator.EQ, "ACTIVE");
        QuerySort sort = new QuerySort("createdAt", SortDirection.DESC);
        ExportSpec spec = ExportSpec.builder()
            .rootType(String.class)
            .scene("export-scene")
            .attributes(singleton("scopeKey", "scopeValue"))
            .filters(Collections.singletonList(filter))
            .sorts(Collections.singletonList(sort))
            .page(new PageRequest(2, 50))
            .limit(100)
            .fields(Arrays.asList("id", "name"))
            .build();

        resolver.resolveExportScope(null, subject, spec);

        QuerySpec<?> bridged = resolver.querySpec;
        Assertions.assertNotNull(bridged);
        Assertions.assertEquals(String.class, bridged.getRootType());
        Assertions.assertEquals("export-scene", bridged.getScene());
        Assertions.assertEquals("scopeValue", bridged.getAttributes().get("scopeKey"));
        Assertions.assertEquals("status", bridged.getFilters().get(0).getField());
        Assertions.assertEquals("createdAt", bridged.getSorts().get(0).getField());
        Assertions.assertEquals(2, bridged.getPage().getPage());
        Assertions.assertEquals(50, bridged.getPage().getLimit());
        Assertions.assertEquals(Integer.valueOf(100), bridged.getLimit());
        Assertions.assertEquals(Arrays.asList("id", "name"), bridged.getSelectFields());
    }

    private static SubjectContext subject() {
        SubjectContext subject = new SubjectContext();
        subject.setSubjectId("tester");
        subject.setTenantId("tenant-a");
        subject.setOrgId("org-a");
        return subject;
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put(key, value);
        return values;
    }

    private static final class CapturingResolver implements CrudDataScopeResolver {
        private QuerySpec<?> querySpec;
        private CommandSpec<?> commandSpec;

        @Override
        public CrudDataScope resolveQueryScope(CrudResourceAction action, SubjectContext subject, QuerySpec<?> spec) {
            this.querySpec = spec;
            return CrudDataScope.allowAll();
        }

        @Override
        public CrudDataScope resolveCommandScope(CrudResourceAction action, SubjectContext subject, CommandSpec<?> spec) {
            this.commandSpec = spec;
            return CrudDataScope.allowAll();
        }
    }
}
