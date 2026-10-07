package com.entloom.crud.core.governance.policy;

import com.entloom.crud.annotations.EntCrudAction;
import com.entloom.crud.annotations.EntCrudActions;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.ExportOperation;
import com.entloom.crud.api.enums.ImportOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrudOperationMappingRegistryTest {
    @Test
    void maps_each_domain_independently_and_requires_action_scene() {
        CrudOperationMappingRegistry registry = registry(
            mapping(QueryOperation.LIST, null, "view", "Project#list"),
            mapping(StatsOperation.QUERY, null, "stats", "Project#stats"),
            mapping(ImportOperation.SUBMIT, null, "import", "Project#import"),
            mapping(ExportOperation.SUBMIT, null, "export", "Project#export"),
            mapping(CommandOperation.ACTION, "refund", "refund", "Project#refund"));
        assertEquals(new BusinessOperation("order", "view"),
            registry.require("order", CrudOperationKey.of(QueryOperation.LIST), "search"));
        assertEquals(new BusinessOperation("order", "stats"),
            registry.require("order", CrudOperationKey.of(StatsOperation.QUERY), null));
        assertEquals(new BusinessOperation("order", "import"),
            registry.require("order", CrudOperationKey.of(ImportOperation.SUBMIT), null));
        assertEquals(new BusinessOperation("order", "export"),
            registry.require("order", CrudOperationKey.of(ExportOperation.SUBMIT), null));
        assertEquals(new BusinessOperation("order", "refund"),
            registry.require("order", CrudOperationKey.of(CommandOperation.ACTION), " REFUND "));
        assertThrows(PermissionDeniedException.class, () -> registry.require(
            "order", CrudOperationKey.of(QueryOperation.DETAIL), null));
        assertThrows(PermissionDeniedException.class, () -> registry.require(
            "order", CrudOperationKey.of(ImportOperation.COMMIT), null));
        assertThrows(PermissionDeniedException.class, () -> registry.require(
            "order", CrudOperationKey.of(ExportOperation.DOWNLOAD), null));
        assertThrows(PermissionDeniedException.class, () -> registry.require(
            "order", CrudOperationKey.of(CommandOperation.ACTION), "approve"));
        assertThrows(PermissionDeniedException.class, () -> registry.require(
            "OrderEntity", CrudOperationKey.of(QueryOperation.LIST), null));
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().clear());
    }

    @Test
    void rejects_invalid_scene_and_entity_action_boundary() {
        assertThrows(ValidationException.class, () -> mapping(CommandOperation.ACTION, null, "refund", "Project#refund"));
        assertThrows(ValidationException.class, () -> mapping(QueryOperation.LIST, "refund", "view", "Project#list"));
        ValidationException error = assertThrows(ValidationException.class, () -> registry(
            mapping(CommandOperation.ACTION, "approve", "refund", "Project#approve")));
        assertTrue(error.getMessage().contains("实体动作边界"));
    }

    @Test
    void rejects_unknown_target_and_duplicate_sources() {
        ValidationException unknown = assertThrows(ValidationException.class, () -> registry(
            mapping(QueryOperation.LIST, null, "missing", "Project#missing")));
        assertTrue(unknown.getMessage().contains("Project#missing"));
        ValidationException duplicate = assertThrows(ValidationException.class, () -> registry(
            mapping(QueryOperation.LIST, null, "view", "Project#list"),
            mapping(QueryOperation.LIST, null, "stats", "Other#list")));
        assertTrue(duplicate.getMessage().contains("Project#list"));
        assertTrue(duplicate.getMessage().contains("Other#list"));
        BusinessOperationCatalog catalog = catalog();
        ValidationException resource = assertThrows(ValidationException.class, () ->
            new CrudOperationMappingRegistry(Collections.singletonList(entity()), catalog,
                Collections.singletonList(new CrudOperationMapping(CrudOperationKey.of(QueryOperation.LIST), null,
                    new BusinessOperation("other", "view"), "Project#other"))));
        assertTrue(resource.getMessage().contains("Project#other"));
    }

    private static CrudOperationMappingRegistry registry(CrudOperationMapping... mappings) {
        return new CrudOperationMappingRegistry(Collections.singletonList(entity()), catalog(), Arrays.asList(mappings));
    }

    private static CrudOperationMapping mapping(com.entloom.crud.api.enums.CrudScopedOperation operation,
                                                String scene, String code, String source) {
        return new CrudOperationMapping(CrudOperationKey.of(operation), scene,
            new BusinessOperation("order", code), source);
    }

    private static BusinessOperationCatalog catalog() {
        return new BusinessOperationCatalog(Collections.singletonList(entity().getResourceDescriptor()), Arrays.asList(
            "view", "stats", "import", "export", "refund").stream()
            .map(code -> new BusinessOperationDeclaration(new BusinessOperation("order", code), "Project#" + code))
            .collect(java.util.stream.Collectors.toList()));
    }

    private static EntityMeta entity() {
        return new EntityMeta(OrderEntity.class,
            new ResourceDescriptor(OrderEntity.class, "order", null, Collections.emptyList()),
            "order", "id", null, Collections.emptyMap());
    }

    @EntCrudActions({@EntCrudAction(value = "refund", name = "退款", capability = "refund")})
    private static final class OrderEntity {
    }
}
