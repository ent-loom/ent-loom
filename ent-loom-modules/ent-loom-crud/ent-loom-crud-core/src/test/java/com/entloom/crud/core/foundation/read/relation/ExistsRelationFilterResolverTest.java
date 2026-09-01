package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.query.spec.ExistsRelationFilter;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.enums.RelationScope;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ExistsRelationFilterResolverTest {
    @Test
    void should_resolve_local_root_outgoing_relation_with_target_direct_field() {
        TestMetaRegistry registry = new TestMetaRegistry(RelationScope.LOCAL_DB, true);

        ResolvedExistsRelationFilter resolved = new ExistsRelationFilterResolver(registry).resolve(
            registry.rootMeta(),
            registry.graph(),
            existsFilter("items", "code")
        );

        Assertions.assertEquals(OrderItemEntity.class, resolved.getTargetMeta().getEntityType());
        Assertions.assertEquals("items", resolved.getRelationEdge().getRelationField());
    }

    @Test
    void should_reject_relation_that_is_not_root_outgoing_edge() {
        TestMetaRegistry registry = new TestMetaRegistry(RelationScope.LOCAL_DB, false);

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new ExistsRelationFilterResolver(registry).resolve(
                registry.rootMeta(),
                registry.graph(),
                existsFilter("items", "code")
            )
        );

        Assertions.assertTrue(ex.getMessage().contains("未找到关联关系"));
    }

    @Test
    void should_reject_non_local_relation() {
        TestMetaRegistry registry = new TestMetaRegistry(RelationScope.REMOTE_SERVICE, true);

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new ExistsRelationFilterResolver(registry).resolve(
                registry.rootMeta(),
                registry.graph(),
                existsFilter("items", "code")
            )
        );

        Assertions.assertTrue(ex.getMessage().contains("LOCAL_DB"));
    }

    @Test
    void should_reject_non_direct_target_field() {
        TestMetaRegistry registry = new TestMetaRegistry(RelationScope.LOCAL_DB, true);

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new ExistsRelationFilterResolver(registry).resolve(
                registry.rootMeta(),
                registry.graph(),
                existsFilter("items", "detail.code")
            )
        );

        Assertions.assertTrue(ex.getMessage().contains("目标实体直接字段"));
    }

    private ExistsRelationFilter existsFilter(String relation, String field) {
        return new ExistsRelationFilter(
            relation,
            Collections.singletonList(new QueryFilter(field, FilterOperator.EQ, "ITEM-1"))
        );
    }

    private static final class TestMetaRegistry implements EntityMetaRegistry {
        private final Map<Class<?>, EntityMeta> metas;
        private final RelationGraph graph;

        private TestMetaRegistry(RelationScope scope, boolean rootOutgoing) {
            this.metas = new LinkedHashMap<Class<?>, EntityMeta>();
            metas.put(OrderEntity.class, entityMeta(OrderEntity.class, "order", orderFields()));
            metas.put(OrderItemEntity.class, entityMeta(OrderItemEntity.class, "order_item", orderItemFields()));
            metas.put(IntermediateEntity.class, entityMeta(IntermediateEntity.class, "intermediate", orderFields()));
            RelationEdge rootToItem = edge(OrderEntity.class, OrderItemEntity.class, "items", scope);
            RelationEdge intermediateToItem = edge(IntermediateEntity.class, OrderItemEntity.class, "items", scope);
            this.graph = RelationGraph.of(rootOutgoing
                ? Arrays.asList(rootToItem, intermediateToItem)
                : Collections.singletonList(intermediateToItem));
        }

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

        private EntityMeta rootMeta() {
            return getEntityMeta(OrderEntity.class);
        }

        private RelationGraph graph() {
            return graph;
        }

        private static EntityMeta entityMeta(
            Class<?> entityType,
            String resourceCode,
            Map<String, EntityFieldMeta> fields
        ) {
            return new EntityMeta(
                entityType,
                new ResourceDescriptor(entityType, resourceCode, "test", Collections.<String>emptyList()),
                "t_" + resourceCode,
                "id",
                "isDeleted",
                fields
            );
        }

        private static RelationEdge edge(
            Class<?> fromEntity,
            Class<?> toEntity,
            String relationField,
            RelationScope scope
        ) {
            RelationEdge edge = new RelationEdge();
            edge.setFromEntity(fromEntity);
            edge.setToEntity(toEntity);
            edge.setRelationField(relationField);
            edge.setFromField("id");
            edge.setToField("orderId");
            edge.setScope(scope);
            return edge;
        }

        private static Map<String, EntityFieldMeta> orderFields() {
            return fields("id", "isDeleted");
        }

        private static Map<String, EntityFieldMeta> orderItemFields() {
            return fields("id", "orderId", "code", "isDeleted");
        }

        private static Map<String, EntityFieldMeta> fields(String... names) {
            Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
            for (String name : names) {
                fields.put(name, new EntityFieldMeta(name, String.class, toColumnName(name), true, false, true, true));
            }
            return Collections.unmodifiableMap(fields);
        }

        private static String toColumnName(String field) {
            if ("orderId".equals(field)) {
                return "order_id";
            }
            if ("isDeleted".equals(field)) {
                return "is_deleted";
            }
            return field;
        }
    }

    private static final class OrderEntity {
    }

    private static final class OrderItemEntity {
    }

    private static final class IntermediateEntity {
    }
}
