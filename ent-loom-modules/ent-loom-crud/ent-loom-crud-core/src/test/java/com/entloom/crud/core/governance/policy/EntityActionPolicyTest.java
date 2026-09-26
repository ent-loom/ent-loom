package com.entloom.crud.core.governance.policy;

import com.entloom.crud.annotations.EntCrudAction;
import com.entloom.crud.annotations.EntCrudActions;
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
        ScenePolicyRegistry registry = new ScenePolicyRegistry(Collections.singletonList(policy("base", "place")));
        ScenePolicyMatch match = match(registry, ClosedOrder.class, "place", "base", null);
        assertFalse(match.isMatched());
        assertTrue(match.getRejectionReason().contains("实体动作边界"));
    }

    @Test
    void should_reject_duplicate_keys_from_annotations_beans_and_mixed_sources() {
        assertThrows(ValidationException.class, () -> registry(DuplicateOrder.class));
        assertThrows(ValidationException.class, () -> registry(PlainOrder.class, policy("base", "place"), policy("BASE", "PLACE")));
        assertThrows(ValidationException.class, () -> registry(MultiEntryOrder.class, policy("base", "place")));
    }

    @Test
    void should_reject_invalid_action_metadata() {
        for (Class<?> type : Arrays.<Class<?>>asList(ConflictingNames.class, BlankScene.class,
            BlankCapability.class, BlankEntry.class, BlankName.class, BlankPortal.class)) {
            assertThrows(ValidationException.class, () -> registry(type), type.getSimpleName());
        }
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
        return ScenePolicyRegistry.create(entities, Arrays.asList(policies));
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

    @EntCrudActions({
        @EntCrudAction(value = "place", name = "下单", capability = "place-order"),
        @EntCrudAction(value = "PLACE", name = "下单", accessEntry = "MANAGEMENT", capability = "manage-order", portals = "HTTP")
    })
    private static class MultiEntryOrder {
    }

    private static class PlainOrder {
    }

    @EntCrudActions
    private static class ClosedOrder {
    }

    @EntCrudActions({
        @EntCrudAction(value = "place", name = "下单", capability = "place-order"),
        @EntCrudAction(value = " PLACE ", name = "下单", accessEntry = " BASE ", capability = "place-order")
    })
    private static class DuplicateOrder {
    }

    @EntCrudActions({
        @EntCrudAction(value = "place", name = "下单", capability = "place-order"),
        @EntCrudAction(value = "place", name = "其他名称", accessEntry = "consumer", capability = "place-order")
    })
    private static class ConflictingNames {
    }

    @EntCrudActions(@EntCrudAction(value = " ", name = "下单", capability = "place-order"))
    private static class BlankScene {
    }

    @EntCrudActions(@EntCrudAction(value = "place", name = "下单", capability = " "))
    private static class BlankCapability {
    }

    @EntCrudActions(@EntCrudAction(value = "place", name = "下单", capability = "place-order", accessEntry = " "))
    private static class BlankEntry {
    }

    @EntCrudActions(@EntCrudAction(value = "place", name = " ", capability = "place-order"))
    private static class BlankName {
    }

    @EntCrudActions(@EntCrudAction(value = "place", name = "下单", capability = "place-order", portals = " "))
    private static class BlankPortal {
    }
}
