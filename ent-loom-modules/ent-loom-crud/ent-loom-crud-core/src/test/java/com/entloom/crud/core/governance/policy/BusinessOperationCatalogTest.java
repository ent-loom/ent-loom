package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessOperationCatalogTest {
    @Test
    void declares_distinct_business_actions_without_inheriting_crud_permissions() {
        BusinessOperation view = new BusinessOperation("order", "view");
        BusinessOperation refund = new BusinessOperation("order", "refund");
        BusinessOperationCatalog catalog = catalog(
            declaration(view, "OrderPolicy#view"), declaration(refund, "OrderPolicy#refund"));
        assertEquals("OrderPolicy#view", catalog.require(view).getSource());
        assertEquals("OrderPolicy#refund", catalog.require(refund).getSource());
        assertThrows(PermissionDeniedException.class, () -> catalog.require(new BusinessOperation("order", "update")));
        assertThrows(PermissionDeniedException.class, () -> catalog.require(new BusinessOperation("customer", "view")));
        assertThrows(UnsupportedOperationException.class, () -> catalog.snapshot().clear());
    }

    @Test
    void rejects_duplicate_operation_with_both_declaration_sources() {
        BusinessOperation operation = new BusinessOperation("order", "view");
        ValidationException error = assertThrows(ValidationException.class, () -> catalog(
            declaration(operation, "OrderPolicy#view"), declaration(operation, "OrderEntity#view")));
        assertTrue(error.getMessage().contains("OrderPolicy#view"));
        assertTrue(error.getMessage().contains("OrderEntity#view"));
    }

    @Test
    void rejects_unknown_resource_at_compile_time() {
        ValidationException error = assertThrows(ValidationException.class, () -> catalog(
            declaration(new BusinessOperation("missing", "view"), "MissingPolicy#view")));
        assertTrue(error.getMessage().contains("missing.view"));
        assertTrue(error.getMessage().contains("MissingPolicy#view"));
    }

    @Test
    void rejects_ambiguous_resources_and_invalid_codes() {
        assertThrows(ValidationException.class, () -> new BusinessOperationCatalog(
            Arrays.asList(resource(), resource()), Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new BusinessOperation(" order", "view"));
        assertThrows(IllegalArgumentException.class, () -> new BusinessOperation("order", " "));
        assertThrows(ValidationException.class, () -> declaration(new BusinessOperation("order", "view"), " "));
    }

    private static BusinessOperationCatalog catalog(BusinessOperationDeclaration... declarations) {
        return new BusinessOperationCatalog(Collections.singletonList(resource()), Arrays.asList(declarations));
    }

    private static ResourceDescriptor resource() {
        return new ResourceDescriptor(Object.class, "order", null, Collections.emptyList());
    }

    private static BusinessOperationDeclaration declaration(BusinessOperation operation, String source) {
        return new BusinessOperationDeclaration(operation, source);
    }
}
