package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCrudCommandHandlerTest {
    @Test
    void create_should_treat_blank_id_as_null_and_use_generated_key() {
        EntityMeta meta = testMeta(EntityIdPolicy.GENERATED);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        executor.generatedKey = Long.valueOf(9527L);
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "   ");
        payload.put("name", "Alice");
        Map<String, Object> result = handler.create(spec(CommandOperation.CREATE, payload, Map.class));

        assertTrue(executor.insertCalled);
        assertFalse(executor.updateCalled);
        assertFalse(executor.lastInsertArgs.contains(""));
        assertEquals(Long.valueOf(9527L), result.get("id"));
    }

    @Test
    void create_should_insert_explicit_id_without_writable_field_check() {
        EntityMeta meta = testMeta(EntityIdPolicy.EXPLICIT);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1001L);
        payload.put("name", "Alice");
        Map<String, Object> result = handler.create(spec(CommandOperation.CREATE, payload, Map.class));

        assertFalse(executor.insertCalled);
        assertTrue(executor.updateCalled);
        assertEquals(Long.valueOf(1001L), result.get("id"));
        assertEquals(Long.valueOf(1001L), executor.lastUpdateArgs.get(0));
    }

    @Test
    void create_should_reject_mismatched_write_command_id_and_payload_id() {
        EntityMeta meta = testMeta(EntityIdPolicy.EXPLICIT);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Object, Map<String, Object>> handler = new JdbcCrudCommandHandler<Object, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("id", 1002L);
        values.put("name", "Alice");

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.create(objectSpec(CommandOperation.CREATE, new WriteCommand<Map<String, Object>>(CommandOperation.CREATE, 1001L, values), Map.class))
        );

        assertTrue(ex.getMessage().contains("主键字段不一致"));
        assertFalse(executor.updateCalled);
    }

    @Test
    void create_should_reject_explicit_id_for_generated_policy() {
        EntityMeta meta = testMeta(EntityIdPolicy.GENERATED);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1003L);
        payload.put("name", "Alice");

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.create(spec(CommandOperation.CREATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("GENERATED"));
        assertFalse(executor.insertCalled);
    }

    @Test
    void save_or_update_should_create_without_id_for_generated_policy() {
        EntityMeta meta = testMeta(EntityIdPolicy.GENERATED);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        executor.generatedKey = Long.valueOf(9529L);
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "Alice");
        Map<String, Object> result = handler.saveOrUpdate(spec(CommandOperation.SAVE_OR_UPDATE, payload, Map.class));

        assertTrue(executor.insertCalled);
        assertFalse(executor.updateCalled);
        assertEquals(CommandOperation.CREATE.name(), result.get("operation"));
        assertEquals(Long.valueOf(9529L), result.get("id"));
    }

    @Test
    void update_should_reject_missing_target_filters() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "");
        payload.put("name", "Alice");
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.update(spec(CommandOperation.UPDATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("payload.id"));
    }

    @Test
    void update_should_reject_scope_field_payload() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("schoolId", 198L);

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.update(spec(CommandOperation.UPDATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("schoolId"));
        assertFalse(executor.updateCalled);
    }

    @Test
    void update_should_reject_immutable_field_payload() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("updateTime", "2026-05-03 21:00:00");

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.update(spec(CommandOperation.UPDATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("updateTime"));
        assertFalse(executor.updateCalled);
    }

    @Test
    void update_should_ignore_unchanged_non_writable_field_when_option_enabled() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        Map<String, Object> current = new LinkedHashMap<String, Object>();
        current.put("school_id", 198L);
        executor.queryRows = Collections.singletonList(current);
        JdbcCrudCommandOptions options = new JdbcCrudCommandOptions();
        options.setIgnoreUnchangedNonWritableUpdateFields(true);
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor,
            options
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("schoolId", "198");
        payload.put("name", "Alice");

        Map<String, Object> result = handler.update(spec(CommandOperation.UPDATE, payload, Map.class));

        assertTrue(executor.queryForListCalled);
        assertTrue(executor.updateCalled);
        assertEquals(Integer.valueOf(1), result.get("rows"));
        assertEquals("Alice", executor.lastUpdateArgs.get(0));
        assertEquals(1L, executor.lastUpdateArgs.get(1));
    }

    @Test
    void update_should_reject_changed_non_writable_field_when_option_enabled() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        Map<String, Object> current = new LinkedHashMap<String, Object>();
        current.put("school_id", 198L);
        executor.queryRows = Collections.singletonList(current);
        JdbcCrudCommandOptions options = new JdbcCrudCommandOptions();
        options.setIgnoreUnchangedNonWritableUpdateFields(true);
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor,
            options
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("schoolId", 199L);
        payload.put("name", "Alice");

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.update(spec(CommandOperation.UPDATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("schoolId"));
        assertFalse(executor.updateCalled);
    }

    @Test
    void update_should_ignore_changed_non_writable_field_when_ignore_option_enabled() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandOptions options = new JdbcCrudCommandOptions();
        options.setIgnoreNonWritableUpdateFields(true);
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor,
            options
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("schoolId", 199L);
        payload.put("updateTime", "2026-05-20 18:10:00");
        payload.put("name", "Alice");

        Map<String, Object> result = handler.update(spec(CommandOperation.UPDATE, payload, Map.class));

        assertFalse(executor.queryForListCalled);
        assertTrue(executor.updateCalled);
        assertEquals(Integer.valueOf(1), result.get("rows"));
        assertEquals(2, executor.lastUpdateArgs.size());
        assertEquals("Alice", executor.lastUpdateArgs.get(0));
        assertEquals(1L, executor.lastUpdateArgs.get(1));
    }

    @Test
    void create_should_allow_scope_field_only_from_governance_scope() {
        EntityMeta meta = testMeta(EntityIdPolicy.GENERATED);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        executor.generatedKey = Long.valueOf(9528L);
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "Alice");
        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put("schoolId", 198L);

        handler.create(spec(CommandOperation.CREATE, payload, Map.class, CrudDataScope.scoped(dimensions)));

        assertTrue(executor.insertCalled);
        assertTrue(executor.lastInsertArgs.contains(198L));
    }

    @Test
    void create_should_reject_scope_field_without_governance_scope() {
        EntityMeta meta = testMeta();
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "Alice");
        payload.put("schoolId", 198L);

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.create(spec(CommandOperation.CREATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("schoolId"));
        assertFalse(executor.insertCalled);
    }

    @Test
    void create_should_allow_scope_field_without_governance_scope_when_resource_is_not_strict() {
        EntityMeta meta = testMeta(EntityIdPolicy.GENERATED);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        executor.generatedKey = Long.valueOf(9528L);
        JdbcCrudCommandOptions options = new JdbcCrudCommandOptions();
        options.setCreateScopeFieldValidationMode(JdbcCrudCommandOptions.CreateScopeFieldValidationMode.STRICT_RESOURCES);
        options.setStrictCreateScopeFieldResources(Collections.singleton("OtherEntity"));
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor,
            options
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "Alice");
        payload.put("schoolId", 198L);

        handler.create(spec(CommandOperation.CREATE, payload, Map.class));

        assertTrue(executor.insertCalled);
        assertTrue(executor.lastInsertArgs.contains(198L));
    }

    @Test
    void create_should_reject_scope_field_without_governance_scope_when_resource_is_strict() {
        EntityMeta meta = testMeta(EntityIdPolicy.GENERATED);
        RecordingGuardedSqlExecutor executor = new RecordingGuardedSqlExecutor();
        JdbcCrudCommandOptions options = new JdbcCrudCommandOptions();
        options.setCreateScopeFieldValidationMode(JdbcCrudCommandOptions.CreateScopeFieldValidationMode.STRICT_RESOURCES);
        options.setStrictCreateScopeFieldResources(Collections.singleton("TestEntity"));
        JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>> handler = new JdbcCrudCommandHandler<Map<String, Object>, Map<String, Object>>(
            new SingleEntityMetaRegistry(meta),
            executor,
            options
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "Alice");
        payload.put("schoolId", 198L);

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> handler.create(spec(CommandOperation.CREATE, payload, Map.class))
        );

        assertTrue(ex.getMessage().contains("schoolId"));
        assertFalse(executor.insertCalled);
    }

    private static EntityMeta testMeta() {
        return testMeta(EntityIdPolicy.EXPLICIT);
    }

    private static EntityMeta testMeta(EntityIdPolicy idPolicy) {
        LinkedHashMap<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("name", new EntityFieldMeta("name", String.class, "name", true, false, true, true));
        fields.put("schoolId", new EntityFieldMeta("schoolId", Long.class, "school_id", false, false, true, true, false, true, false));
        fields.put("updateTime", new EntityFieldMeta("updateTime", String.class, "update_time", true, false, true, true, false, false, true));
        return new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "TestEntity", "test-service", Collections.<String>emptyList()),
            "t_test",
            "id",
            idPolicy,
            null,
            fields
        );
    }

    private static CommandSpec<Map<String, Object>> spec(CommandOperation op, Map<String, Object> payload, Class<?> resultType) {
        return spec(op, payload, resultType, null);
    }

    private static CommandSpec<Object> objectSpec(CommandOperation op, Object payload, Class<?> resultType) {
        return CommandSpec.<Object>builder()
            .op(op)
            .scene("unit-test")
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .payload(payload)
            .resultType(resultType)
            .build();
    }

    private static CommandSpec<Map<String, Object>> spec(
        CommandOperation op,
        Map<String, Object> payload,
        Class<?> resultType,
        CrudDataScope governanceScope
    ) {
        return CommandSpec.<Map<String, Object>>builder()
            .op(op)
            .scene("unit-test")
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .payload(payload)
            .resultType(resultType)
            .governanceScope(governanceScope)
            .build();
    }

    private static final class TestEntity {
    }

    private static final class SingleEntityMetaRegistry implements EntityMetaRegistry {
        private final EntityMeta meta;

        private SingleEntityMetaRegistry(EntityMeta meta) {
            this.meta = meta;
        }

        @Override
        public EntityMeta getEntityMeta(Class<?> entityType) {
            if (!meta.getEntityType().equals(entityType)) {
                throw new IllegalArgumentException("unknown entityType: " + entityType);
            }
            return meta;
        }

        @Override
        public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
            return getEntityMeta(entityType).getResourceDescriptor();
        }

        @Override
        public RelationGraph getRelationGraph(Class<?> rootType) {
            return RelationGraph.empty();
        }

        @Override
        public void validateOrThrow() {
            // no-op for unit test
        }
    }

    private static final class RecordingGuardedSqlExecutor implements GuardedSqlExecutor {
        private boolean insertCalled;
        private boolean updateCalled;
        private boolean queryForListCalled;
        private List<Object> lastInsertArgs = Collections.emptyList();
        private List<Object> lastUpdateArgs = Collections.emptyList();
        private List<Map<String, Object>> queryRows = Collections.emptyList();
        private Object generatedKey;

        @Override
        public List<Map<String, Object>> queryForList(String sql, List<Object> args, CrudExecutionContext context) {
            queryForListCalled = true;
            return queryRows;
        }

        @Override
        public Map<String, Object> queryForMap(String sql, List<Object> args, CrudExecutionContext context) {
            throw new UnsupportedOperationException("not needed in unit test");
        }

        @Override
        public Object queryForObject(String sql, List<Object> args, CrudExecutionContext context) {
            throw new UnsupportedOperationException("not needed in unit test");
        }

        @Override
        public int update(String sql, List<Object> args, CrudExecutionContext context) {
            updateCalled = true;
            lastUpdateArgs = new ArrayList<Object>(args);
            return 1;
        }

        @Override
        public Object insertAndReturnGeneratedKey(String sql, List<Object> args, CrudExecutionContext context) {
            insertCalled = true;
            lastInsertArgs = new ArrayList<Object>(args);
            return generatedKey;
        }
    }
}
