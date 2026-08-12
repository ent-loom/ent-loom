package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AbstractTargetDeleteHandlerTest {
    @Test
    void should_bridge_delete_target_to_write_command_without_entity_binding() {
        TestTargetDeleteHandler handler = new TestTargetDeleteHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 11L);
        payload.put("name", "ignored");

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        Object result = handler.handle(spec(payload), newDelegate(delegateSpecs));

        Assertions.assertEquals("delegate", result);
        Assertions.assertEquals(11L, handler.target.getId());
        Assertions.assertEquals(1, delegateSpecs.size());
        Object delegatePayload = delegateSpecs.get(0).getPayload();
        Assertions.assertTrue(delegatePayload instanceof WriteCommand);
        @SuppressWarnings("unchecked")
        WriteCommand<Map<String, Object>> command = (WriteCommand<Map<String, Object>>) delegatePayload;
        Assertions.assertEquals(CommandOperation.DELETE, command.getOp());
        Assertions.assertEquals(11L, command.getId());
        Assertions.assertEquals(9L, command.getExpectedVersion().longValue());
        Assertions.assertTrue(command.getValues().isEmpty());
    }

    @Test
    void should_preserve_target_filters_when_id_absent() {
        TestTargetDeleteHandler handler = new TestTargetDeleteHandler(registry());
        CommandSpec<Object> spec = spec(Collections.<String, Object>emptyMap()).toBuilder()
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
    void should_accept_write_command_as_delete_target_input() {
        TestTargetDeleteHandler handler = new TestTargetDeleteHandler(registry());
        List<QueryFilter> commandFilters = Collections.singletonList(new QueryFilter("name", FilterOperator.EQ, "A"));
        WriteCommand<Map<String, Object>> payload = new WriteCommand<Map<String, Object>>(
            CommandOperation.DELETE,
            33L,
            Collections.<String, Object>emptyMap(),
            commandFilters,
            7L
        );
        CommandSpec<Object> spec = spec(payload).toBuilder()
            .expectedVersion(9L)
            .targetFilters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, 99L)))
            .build();

        final List<CommandSpec<Object>> delegateSpecs = new ArrayList<CommandSpec<Object>>();
        handler.handle(spec, newDelegate(delegateSpecs));

        @SuppressWarnings("unchecked")
        WriteCommand<Map<String, Object>> command =
            (WriteCommand<Map<String, Object>>) delegateSpecs.get(0).getPayload();
        Assertions.assertEquals(33L, command.getId());
        Assertions.assertEquals(7L, command.getExpectedVersion().longValue());
        Assertions.assertEquals("name", command.getTargetFilters().get(0).getField());
        Assertions.assertEquals(7L, handler.targetSpec.getExpectedVersion().longValue());
        Assertions.assertEquals("name", handler.targetSpec.getTargetFilters().get(0).getField());
    }

    @Test
    void should_reject_delete_without_id_or_target_filters() {
        TestTargetDeleteHandler handler = new TestTargetDeleteHandler(registry());

        Assertions.assertThrows(
            ValidationException.class,
            () -> handler.handle(spec(Collections.<String, Object>emptyMap()), newDelegate(new ArrayList<CommandSpec<Object>>()))
        );
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

    private CommandSpec<Object> spec(Object payload) {
        return CommandSpec.<Object>builder()
            .op(CommandOperation.DELETE)
            .scene("delete")
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

    static class TestTargetDeleteHandler extends AbstractTargetDeleteHandler<TestEntity, Object> {
        private DeleteTarget target;
        private CommandSpec<DeleteTarget> targetSpec;

        TestTargetDeleteHandler(EntityMetaRegistry entityMetaRegistry) {
            super(entityMetaRegistry, TestEntity.class, "delete");
        }

        @Override
        protected Object handleDelete(
            CommandSpec<DeleteTarget> spec,
            SceneDelegate<CommandSpec<Object>, Object> delegate
        ) {
            targetSpec = spec;
            target = spec.getPayload();
            return invokeDelegateDelete(spec, delegate);
        }
    }

    public static class TestEntity {
        public Long id;
        public String name;
    }
}
