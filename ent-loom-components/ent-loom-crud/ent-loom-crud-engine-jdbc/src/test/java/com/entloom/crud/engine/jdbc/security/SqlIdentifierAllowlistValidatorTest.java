package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SqlIdentifierAllowlistValidatorTest {
    @Test
    void validate_command_spec_should_check_crud_record_payload_fields() {
        SqlIdentifierAllowlistValidator validator = new SqlIdentifierAllowlistValidator(testMetaRegistry());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", 1L);
        payload.put("illegalField", "x");
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .op(CommandOperation.CREATE)
            .payload(CrudRecord.copyOf(payload))
            .resultType(Map.class)
            .build();

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> validator.validateCommandSpec(spec));

        Assertions.assertTrue(ex.getMessage().contains("未知载荷字段"));
    }

    @Test
    void validate_command_spec_should_reject_non_map_payload() {
        SqlIdentifierAllowlistValidator validator = new SqlIdentifierAllowlistValidator(testMetaRegistry());
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .op(CommandOperation.CREATE)
            .payload(new Object())
            .resultType(Map.class)
            .build();

        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> validator.validateCommandSpec(spec));

        Assertions.assertTrue(ex.getMessage().contains("命令载荷必须是 Map、CrudRecord 或 WriteCommand"));
    }

    private EntityMetaRegistry testMetaRegistry() {
        final EntityMeta entityMeta = new EntityMeta(
            TestEntity.class,
            new ResourceDescriptor(TestEntity.class, "test_entity", "test", Collections.<String>emptyList()),
            "t_test",
            "id",
            "isDeleted",
            testFieldMetas()
        );
        final RelationGraph graph = RelationGraph.empty();
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return entityMeta;
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return entityMeta.getResourceDescriptor();
            }

            @Override
            public RelationGraph getRelationGraph(Class<?> rootType) {
                return graph;
            }

            @Override
            public void validateOrThrow() {
            }
        };
    }

    private Map<String, EntityFieldMeta> testFieldMetas() {
        LinkedHashMap<String, EntityFieldMeta> metas = new LinkedHashMap<String, EntityFieldMeta>();
        metas.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        metas.put("orderNo", new EntityFieldMeta("orderNo", String.class, "order_no", true, false, true, true));
        metas.put("isDeleted", new EntityFieldMeta("isDeleted", Integer.class, "is_deleted", true, false, true, true));
        return Collections.unmodifiableMap(metas);
    }

    private static final class TestEntity {
        private Long id;
        private String orderNo;
        private Integer isDeleted;
    }
}
