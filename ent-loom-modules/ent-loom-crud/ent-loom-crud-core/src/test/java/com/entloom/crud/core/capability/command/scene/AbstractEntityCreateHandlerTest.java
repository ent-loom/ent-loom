package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.exception.DataScopeDeniedException;
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
        fields.put("schoolId", field("schoolId", Long.class, "school_id"));
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

    static class TestCreateHandler extends AbstractEntityCreateHandler<TestEntity, Object> {
        private final EntityMetaRegistry entityMetaRegistry;
        private TestEntity requested;

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
    }
}
