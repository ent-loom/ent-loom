package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AbstractEntityCreateHandlerTest {
    @Test
    void should_inject_create_scope_into_entity_before_business_handler() {
        TestCreateHandler handler = new TestCreateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "");
        payload.put("name", "A");

        handler.handle(spec(payload, scope("schoolId", 3L)), noopDelegate());

        Assertions.assertNull(handler.requested.id);
        Assertions.assertEquals(Long.valueOf(3L), handler.requested.schoolId);
        Assertions.assertEquals("A", handler.requested.name);
    }

    @Test
    void should_reject_create_scope_conflict_before_business_handler() {
        TestCreateHandler handler = new TestCreateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("schoolId", 9L);
        payload.put("name", "A");

        Assertions.assertThrows(
            DataScopeDeniedException.class,
            () -> handler.handle(spec(payload, scope("schoolId", 3L)), noopDelegate())
        );
        Assertions.assertNull(handler.requested);
    }

    @Test
    void should_validate_required_field_after_scope_injection() {
        TestCreateHandler handler = new TestCreateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "   ");

        ValidationException exception = Assertions.assertThrows(
            ValidationException.class,
            () -> handler.handle(spec(payload, scope("schoolId", 3L)), noopDelegate())
        );

        Assertions.assertEquals("名称不能为空", exception.getMessage());
        Assertions.assertNull(handler.requested);
    }

    @Test
    void should_distinguish_missing_primitive_from_explicit_false() {
        TestCreateHandler handler = new TestCreateHandler(registry(true));
        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("name", "A");

        ValidationException exception = Assertions.assertThrows(
            ValidationException.class,
            () -> handler.handle(spec(missing, null), noopDelegate())
        );
        Assertions.assertEquals("启用状态不能为空", exception.getMessage());

        Map<String, Object> explicitFalse = new LinkedHashMap<String, Object>();
        explicitFalse.put("name", "A");
        explicitFalse.put("enabled", Boolean.FALSE);
        handler.handle(spec(explicitFalse, null), noopDelegate());
        Assertions.assertFalse(handler.requested.enabled);

        TestCreateHandler scopedHandler = new TestCreateHandler(registry(true));
        Map<String, Object> scopedPayload = new LinkedHashMap<String, Object>();
        scopedPayload.put("name", "A");
        scopedHandler.handle(spec(scopedPayload, scope("enabled", Boolean.TRUE)), noopDelegate());
        Assertions.assertTrue(scopedHandler.requested.enabled);
    }

    @Test
    void should_validate_after_prepare_hook() {
        PreparingCreateHandler handler = new PreparingCreateHandler(registry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();

        handler.handle(spec(payload, null), noopDelegate());

        Assertions.assertEquals("prepared", handler.requested.name);
    }

    @Test
    void should_apply_default_value_before_required_validation() {
        TestCreateHandler handler = new TestCreateHandler(registryWithNameDefault());

        handler.handle(spec(Collections.<String, Object>emptyMap(), null), noopDelegate());

        Assertions.assertEquals("默认名称", handler.requested.name);
    }

    private CommandSpec<Object> spec(Map<String, Object> payload, CrudDataScope scope) {
        return CommandSpec.<Object>builder()
            .op(CommandOperation.CREATE)
            .scene("create")
            .rootType(TestEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(TestEntity.class))
            .payload(payload)
            .governanceScope(scope)
            .build();
    }

    private CrudDataScope scope(String field, Object value) {
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put(field, value);
        return CrudDataScope.scoped(dimensions);
    }

    private SceneDelegate<CommandSpec<Object>, Object> noopDelegate() {
        return new SceneDelegate<CommandSpec<Object>, Object>() {
            @Override
            public Object invoke(CommandSpec<Object> spec) {
                return null;
            }
        };
    }

    private EntityMetaRegistry registry() {
        return registry(false);
    }

    private EntityMetaRegistry registry(boolean enabledRequired) {
        final EntityMeta meta = meta(enabledRequired);
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
        return meta(false);
    }

    private EntityMetaRegistry registryWithNameDefault() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, "id"));
        fields.put("schoolId", field("schoolId", Long.class, "school_id"));
        fields.put("name", new EntityFieldMeta(
            "name", String.class, "name", true, false, true, true, true, false, false,
            "名称", true, false, "默认名称"
        ));
        fields.put("enabled", new EntityFieldMeta(
            "enabled", Boolean.TYPE, "enabled", false, false, true, true, true, false, false,
            "启用状态", false
        ));
        final EntityMeta meta = new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "testEntity", "test", Collections.<String>emptyList()),
            "test_entity",
            "id",
            null,
            fields
        );
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

    private EntityMeta meta(boolean enabledRequired) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, "id"));
        fields.put("schoolId", field("schoolId", Long.class, "school_id"));
        fields.put("name", new EntityFieldMeta(
            "name", String.class, "name", true, false, true, true, true, false, false, "名称", true
        ));
        fields.put("enabled", new EntityFieldMeta(
            "enabled", Boolean.TYPE, "enabled", false, false, true, true, true, false, false, "启用状态", enabledRequired
        ));
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

    static class TestCreateHandler extends AbstractEntityCreateHandler<TestEntity, Object> {
        private final EntityMetaRegistry entityMetaRegistry;
        protected TestEntity requested;

        TestCreateHandler(EntityMetaRegistry entityMetaRegistry) {
            this.entityMetaRegistry = entityMetaRegistry;
        }

        @Override
        protected EntityMetaRegistry getEntityMetaRegistry() {
            return entityMetaRegistry;
        }

        @Override
        protected Class<TestEntity> getEntityType() {
            return TestEntity.class;
        }

        @Override
        protected String scene() {
            return "create";
        }

        @Override
        protected Object handleEntity(TestEntity requested) {
            this.requested = requested;
            return "created";
        }
    }

    public static class TestEntity {
        public Long id;
        public Long schoolId;
        public String name;
        public boolean enabled;
    }

    static class PreparingCreateHandler extends TestCreateHandler {
        PreparingCreateHandler(EntityMetaRegistry entityMetaRegistry) {
            super(entityMetaRegistry);
        }

        @Override
        protected void prepareCreateEntity(
            TestEntity requested,
            CommandSpec<Object> spec,
            EntityMeta meta,
            Set<String> presentFields
        ) {
            requested.name = "prepared";
            presentFields.add("name");
        }
    }
}
