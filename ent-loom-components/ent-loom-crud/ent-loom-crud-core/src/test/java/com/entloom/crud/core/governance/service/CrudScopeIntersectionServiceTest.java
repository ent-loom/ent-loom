package com.entloom.crud.core.governance.service;

import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudScopeIntersectionServiceTest {
    private final CrudScopeIntersectionService service = new CrudScopeIntersectionService();

    @Test
    void should_keep_governance_scope_when_business_constraint_is_null_or_all() {
        CrudDataScope governance = CrudDataScope.scoped(dimensions("orgId", "org-a"));

        Assertions.assertSame(governance, service.intersect(governance, null));
        Assertions.assertSame(governance, service.intersect(governance, CrudDataScope.allowAll()));
    }

    @Test
    void should_use_business_constraint_when_governance_scope_is_all() {
        CrudDataScope business = CrudDataScope.scoped(dimensions("orgId", "org-a"));

        Assertions.assertSame(business, service.intersect(CrudDataScope.allowAll(), business));
    }

    @Test
    void should_intersect_list_values_and_preserve_extra_dimensions() {
        Map<String, Object> governanceDimensions = new LinkedHashMap<String, Object>();
        governanceDimensions.put("orgId", Arrays.asList("org-a", "org-b"));
        governanceDimensions.put("tenantId", "tenant-a");
        Map<String, Object> businessDimensions = new LinkedHashMap<String, Object>();
        businessDimensions.put("orgId", Arrays.asList("org-b", "org-c"));

        CrudDataScope result = service.intersect(
            CrudDataScope.scoped(governanceDimensions),
            CrudDataScope.scoped(businessDimensions)
        );

        Assertions.assertEquals("org-b", result.getDimensions().get("orgId"));
        Assertions.assertEquals("tenant-a", result.getDimensions().get("tenantId"));
    }

    @Test
    void should_reject_conflicting_constraints() {
        Assertions.assertThrows(
            DataScopeDeniedException.class,
            () -> service.intersect(
                CrudDataScope.scoped(dimensions("orgId", "org-a")),
                CrudDataScope.scoped(dimensions("orgId", "org-b"))
            )
        );
    }

    @Test
    void should_reject_empty_business_constraint_values() {
        Assertions.assertThrows(
            DataScopeDeniedException.class,
            () -> service.intersect(
                CrudDataScope.scoped(dimensions("orgId", "org-a")),
                CrudDataScope.scoped(dimensions("orgId", Arrays.asList(null, null)))
            )
        );
    }

    private Map<String, Object> dimensions(String key, Object value) {
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put(key, value);
        return dimensions;
    }
}
