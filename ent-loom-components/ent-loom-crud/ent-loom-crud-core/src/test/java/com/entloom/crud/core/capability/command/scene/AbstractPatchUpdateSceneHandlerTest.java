package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.patch.EntityPatch;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AbstractPatchUpdateSceneHandlerTest {
    @Test
    void should_bridge_patch_to_write_command_without_mixing_id_into_values() {
        TestPatchUpdateHandler handler = new TestPatchUpdateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 11L);
        payload.put("name", "new-name");
        payload.put("unknown", "ignored");

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        Object result = handler.handle(spec(payload), newDelegate(delegateSpecs));

        Assertions.assertEquals("delegate", result);
        Assertions.assertTrue(handler.patch.hasField("name"));
        Assertions.assertFalse(handler.patch.hasField("unknown"));
        Assertions.assertEquals(1, delegateSpecs.size());
        Object delegatePayload = delegateSpecs.get(0).getPayload();
        Assertions.assertTrue(delegatePayload instanceof WriteCommand);
        @SuppressWarnings("unchecked")
        WriteCommand<Map<String, Object>> command = (WriteCommand<Map<String, Object>>) delegatePayload;
        Assertions.assertEquals(11L, command.getId());
        Assertions.assertEquals(9L, command.getExpectedVersion().longValue());
        Assertions.assertEquals("new-name", command.getValues().get("name"));
        Assertions.assertFalse(command.getValues().containsKey("id"));
        Assertions.assertFalse(command.getValues().containsKey("unknown"));
    }

    @Test
    void should_preserve_target_filters_when_id_absent() {
        TestPatchUpdateHandler handler = new TestPatchUpdateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "new-name");
        CommandSpec<Object> spec = spec(payload).toBuilder()
            .targetFilters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, 22L)))
            .build();

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        handler.handle(spec, newDelegate(delegateSpecs));

        @SuppressWarnings("unchecked")
        WriteCommand<Map<String, Object>> command =
            (WriteCommand<Map<String, Object>>) delegateSpecs.get(0).getPayload();
        Assertions.assertNull(command.getId());
        Assertions.assertEquals(1, command.getTargetFilters().size());
        Assertions.assertEquals("id", command.getTargetFilters().get(0).getField());
    }

    @Test
    void should_reject_update_without_id_or_target_filters() {
        TestPatchUpdateHandler handler = new TestPatchUpdateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "new-name");

        Assertions.assertThrows(ValidationException.class, () -> handler.handle(spec(payload), newDelegate(new ArrayList<CommandSpec<Object>>())));
    }

    private SceneDelegate<CommandSpec<Object>, Object> newDelegate(final List<CommandSpec<Object>> delegateSpecs) {
        return new SceneDelegate<CommandSpec<Object>, Object>() {
            @Override
            public Object invoke(CommandSpec<Object> delegateSpec) {
                delegateSpecs.add(delegateSpec);
                return "delegate";
            }
        };
    }

    private CommandSpec<Object> spec(Map<String, Object> payload) {
        return CommandSpec.<Object>builder()
            .op(CommandOperation.UPDATE)
            .scene("patch")
            .rootType(TestEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(TestEntity.class))
            .payload(payload)
            .expectedVersion(9L)
            .resultType(Object.class)
            .build();
    }

    private EntityMetaRegistry registry() {
        final EntityMeta meta = meta();
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return meta;
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return meta.getResourceDescriptor();
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return RelationGraph.empty();
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    private EntityMeta meta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, "id"));
        fields.put("name", field("name", String.class, "name"));
        return new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "testEntity", "test", Collections.<String>emptyList()),
            "test_entity",
            "id",
            null,
            fields
        );
    }

    private EntityFieldMeta field(String name, Class<?> type, String column) {
        return new EntityFieldMeta(name, type, column, true, false, true, true);
    }

    static class TestPatchUpdateHandler extends AbstractPatchUpdateSceneHandler<TestEntity, Object> {
        private EntityPatch<TestEntity> patch;

        TestPatchUpdateHandler(EntityMetaRegistry entityMetaRegistry) {
            super(entityMetaRegistry, TestEntity.class, "patch");
        }

        @Override
        protected Object handlePatch(
            CommandSpec<EntityPatch<TestEntity>> spec,
            SceneDelegate<CommandSpec<Object>, Object> delegate
        ) {
            patch = spec.getPayload();
            return invokeDelegateUpdate(spec, delegate);
        }
    }

    public static class TestEntity {
        public Long id;
        public String name;
    }
}
