package com.entloom.crud.core.governance.policy;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityActionPolicyTest {
    @Test
    void should_match_independent_entry_capabilities_and_portals_without_base_fallback() {
        ScenePolicyRegistry registry = registry(MultiEntryOrder.class);
        assertEquals(2, registry.snapshot().size());
        assertEquals("place-order", match(registry, MultiEntryOrder.class, "place", "base", null).getCapability());
        assertEquals("manage-order", match(registry, MultiEntryOrder.class, "PLACE", "management", "http").getCapability());
        assertFalse(match(registry, MultiEntryOrder.class, "place", "management", "sdk").isMatched());
        assertFalse(match(registry, MultiEntryOrder.class, "place", "management", null).isMatched());
        assertFalse(match(registry, MultiEntryOrder.class, "place", "unknown", "http").isMatched());
        assertFalse(match(registry, MultiEntryOrder.class, "cancel", "base", null).isMatched());
    }

    @Test
    void should_allow_programmatic_only_policies_but_deny_unconfigured_actions() {
        ScenePolicyRegistry registry = registry(PlainOrder.class, policy("base", "place"));
        assertTrue(match(registry, PlainOrder.class, "place", "base", null).isMatched());
        assertFalse(match(registry, PlainOrder.class, "cancel", "base", null).isMatched());
        assertFalse(match(registry(PlainOrder.class), PlainOrder.class, "place", "base", null).isMatched());
    }

    @Test
    void should_allow_beans_to_add_entries_only_within_entity_boundary() {
        ScenePolicyRegistry registry = registry(MultiEntryOrder.class, policy("consumer", "place"));
        assertTrue(match(registry, MultiEntryOrder.class, "place", "consumer", null).isMatched());
        assertThrows(ValidationException.class, () -> registry(MultiEntryOrder.class, policy("consumer", "cancel")));
        assertThrows(ValidationException.class, () -> registry(ClosedOrder.class, policy("base", "place")));
    }

    @Test
    void should_enforce_empty_boundary_even_with_manually_built_registry() {
        ScenePolicyRegistry registry = new ScenePolicyRegistry(Collections.singletonList(policy("base", "place")), Collections.singletonMap("order", Collections.emptySet()), Collections.emptyMap());
        ScenePolicyMatch match = match(registry, ClosedOrder.class, "place", "base", null);
        assertFalse(match.isMatched());
        assertTrue(match.getRejectionReason().contains("实体动作边界"));
    }

    @Test
    void should_reject_duplicate_keys_from_compiled_declarations_and_beans() {
        assertThrows(ValidationException.class, () -> registry(DuplicateOrder.class));
        assertThrows(ValidationException.class, () -> registry(PlainOrder.class, policy("base", "place"), policy("BASE", "PLACE")));
        assertThrows(ValidationException.class, () -> registry(MultiEntryOrder.class, policy("base", "place")));
    }

    @Test
    void should_reject_invalid_action_metadata() {
        assertThrows(ValidationException.class, () -> registry(PlainOrder.class, policy("base", " ")));
        ScenePolicy unknown = new ScenePolicy(new ScenePolicyKey("base", "missing",
            CrudOperationKey.of(CommandOperation.ACTION), "place"), "place-order", Collections.<String>emptySet());
        assertThrows(ValidationException.class, () -> registry(PlainOrder.class, unknown));
    }

    private ScenePolicyRegistry registry(Class<?> type, ScenePolicy... policies) {
        EntityMeta meta = new EntityMeta(type, descriptor(type), "orders", "id", null,
            Collections.singletonMap("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true)));
        EntityMetaRegistry entities = new CrudRuntimeModelBackedEntityMetaRegistry(
            CrudRuntimeModel.from(Collections.singletonList(meta), Collections.emptyList()));
        java.util.List<ScenePolicy> compiled = new java.util.ArrayList<>(Arrays.asList(policies));
        java.util.Map<String, java.util.Set<String>> boundary = new java.util.HashMap<>();
        if (type == MultiEntryOrder.class || type == DuplicateOrder.class) {
            boundary.put("order", Collections.singleton("place"));
            compiled.add(policy("base", "place"));
            compiled.add(type == DuplicateOrder.class ? policy("BASE", "PLACE") :
                new ScenePolicy(new ScenePolicyKey("management", "order", CrudOperationKey.of(CommandOperation.ACTION), "place"),
                    "manage-order", Collections.singleton("http")));
        }
        if (type == ClosedOrder.class) boundary.put("order", Collections.emptySet());
        return ScenePolicyRegistry.create(entities, compiled, boundary, Collections.emptyMap());
    }

    private ScenePolicy policy(String entry, String scene) {
        return new ScenePolicy(new ScenePolicyKey(entry, "order", CrudOperationKey.of(CommandOperation.ACTION), scene),
            "place-order", Collections.<String>emptySet());
    }

    private ScenePolicyMatch match(ScenePolicyRegistry registry, Class<?> type, String scene, String entry, String portal) {
        DefaultScenePolicyService service = new DefaultScenePolicyService(registry, spec -> entry, spec -> portal);
        return service.match(new CrudResourceAction(descriptor(type), CrudOperationKey.of(CommandOperation.ACTION), scene, null), null);
    }

    private ResourceDescriptor descriptor(Class<?> type) {
        return new ResourceDescriptor(type, "order", null, null);
    }

    private static class MultiEntryOrder {
    }

    private static class PlainOrder {
    }

    private static class ClosedOrder {
    }

    private static class DuplicateOrder {
    }

    private static class ConflictingNames {
    }

    private static class BlankScene {
    }

    private static class BlankCapability {
    }

    private static class BlankEntry {
    }

    private static class BlankName {
    }

    private static class BlankPortal {
    }
}
