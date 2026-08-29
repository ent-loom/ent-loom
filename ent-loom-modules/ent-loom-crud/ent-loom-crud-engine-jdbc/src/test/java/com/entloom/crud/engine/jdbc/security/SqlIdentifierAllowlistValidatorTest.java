package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.query.spec.ExistsRelationFilter;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.enums.RelationScope;
import com.entloom.crud.enums.QueryStrategy;
import java.util.Arrays;
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

    @Test
    void validate_query_spec_should_accept_exists_relation_field_name() {
        SqlIdentifierAllowlistValidator validator = new SqlIdentifierAllowlistValidator(existsRelationMetaRegistry());
        QuerySpec<RootEntity> spec = existsQuerySpec("target");

        Assertions.assertDoesNotThrow(() -> validator.validateQuerySpec(spec));
    }

    @Test
    void validate_query_spec_should_accept_exists_target_entity_simple_name() {
        SqlIdentifierAllowlistValidator validator = new SqlIdentifierAllowlistValidator(existsRelationMetaRegistry());

        Assertions.assertDoesNotThrow(() -> validator.validateQuerySpec(existsQuerySpec("Target")));
    }

    @Test
    void validate_query_spec_should_reject_ambiguous_exists_root_outgoing_relation() {
        SqlIdentifierAllowlistValidator validator = new SqlIdentifierAllowlistValidator(existsRelationMetaRegistry(true));

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> validator.validateQuerySpec(existsQuerySpec("target"))
        );

        Assertions.assertEquals(CrudErrorCode.ENTITY_SCOPE_ILLEGAL, ex.getErrorCode());
        Assertions.assertTrue(ex.getMessage().contains("关联关系不明确"));
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

    private QuerySpec<RootEntity> existsQuerySpec(String relation) {
        return QuerySpec.<RootEntity>builder()
            .rootType(RootEntity.class)
            .resultType(RootEntity.class)
            .op(QueryOperation.LIST)
            .strategy(QueryStrategy.EXISTS)
            .existsRelationFilter(new ExistsRelationFilter(
                relation,
                Collections.singletonList(new QueryFilter("code", FilterOperator.EQ, "TARGET-A"))
            ))
            .build();
    }

    private EntityMetaRegistry existsRelationMetaRegistry() {
        return existsRelationMetaRegistry(false);
    }

    private EntityMetaRegistry existsRelationMetaRegistry(boolean includeAmbiguousRootRelation) {
        Map<Class<?>, EntityMeta> metas = new LinkedHashMap<Class<?>, EntityMeta>();
        metas.put(RootEntity.class, entityMeta(RootEntity.class, "root"));
        metas.put(IntermediateEntity.class, entityMeta(IntermediateEntity.class, "intermediate"));
        metas.put(TargetEntity.class, entityMeta(TargetEntity.class, "target"));
        metas.put(AlternateTargetEntity.class, entityMeta(AlternateTargetEntity.class, "alternate_target"));
        java.util.List<RelationEdge> edges = Arrays.asList(
            edge(RootEntity.class, TargetEntity.class, "target"),
            edge(RootEntity.class, IntermediateEntity.class, "intermediate"),
            edge(IntermediateEntity.class, TargetEntity.class, "target")
        );
        if (includeAmbiguousRootRelation) {
            edges = new java.util.ArrayList<RelationEdge>(edges);
            edges.add(edge(RootEntity.class, AlternateTargetEntity.class, "target"));
        }
        RelationGraph graph = RelationGraph.of(edges);
        return new EntityMetaRegistry() {
            @Override
            public EntityMeta getEntityMeta(Class<?> entityType) {
                return metas.get(entityType);
            }

            @Override
            public ResourceDescriptor getResourceDescriptor(Class<?> entityType) {
                return getEntityMeta(entityType).getResourceDescriptor();
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

    private EntityMeta entityMeta(Class<?> entityType, String resourceCode) {
        return new EntityMeta(
            entityType,
            new ResourceDescriptor(entityType, resourceCode, "test", Collections.<String>emptyList()),
            "t_" + resourceCode,
            "id",
            "isDeleted",
            testFieldMetas()
        );
    }

    private RelationEdge edge(Class<?> fromEntity, Class<?> toEntity, String relationField) {
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(fromEntity);
        edge.setToEntity(toEntity);
        edge.setRelationField(relationField);
        edge.setFromField("id");
        edge.setToField("rootId");
        edge.setScope(RelationScope.LOCAL_DB);
        return edge;
    }

    private Map<String, EntityFieldMeta> testFieldMetas() {
        LinkedHashMap<String, EntityFieldMeta> metas = new LinkedHashMap<String, EntityFieldMeta>();
        metas.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        metas.put("orderNo", new EntityFieldMeta("orderNo", String.class, "order_no", true, false, true, true));
        metas.put("code", new EntityFieldMeta("code", String.class, "code", true, false, true, true));
        metas.put("isDeleted", new EntityFieldMeta("isDeleted", Integer.class, "is_deleted", true, false, true, true));
        return Collections.unmodifiableMap(metas);
    }

    private static final class TestEntity {
        private Long id;
        private String orderNo;
        private Integer isDeleted;
    }

    private static final class RootEntity {
    }

    private static final class IntermediateEntity {
    }

    private static final class TargetEntity {
    }

    private static final class AlternateTargetEntity {
    }
}
