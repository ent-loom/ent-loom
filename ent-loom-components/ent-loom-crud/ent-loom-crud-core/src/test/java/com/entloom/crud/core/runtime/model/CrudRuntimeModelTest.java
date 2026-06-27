package com.entloom.crud.core.runtime.model;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudRuntimeModelTest {
    @Test
    void runtime_model_should_preserve_entity_identity_field_and_relation_metadata() {
        EntityMeta order = entityMeta(OrderEntity.class, "order", "t_order", EntityIdPolicy.GENERATED);
        EntityMeta item = entityMeta(OrderItemEntity.class, "order_item", "t_order_item", EntityIdPolicy.EXPLICIT);
        RelationEdge edge = relationEdge();

        CrudRuntimeModel model = CrudRuntimeModel.from(Arrays.asList(order, item), Collections.singletonList(edge));

        CrudRuntimeEntityModel orderModel = model.getEntity(OrderEntity.class);
        Assertions.assertEquals("order", orderModel.getResourceDescriptor().getResourceCode());
        Assertions.assertEquals("t_order", orderModel.getTable());
        Assertions.assertEquals(EntityIdPolicy.GENERATED, orderModel.getIdentity().getIdPolicy());
        Assertions.assertEquals("id", orderModel.getIdentity().getIdColumn());
        Assertions.assertEquals(Long.class, orderModel.getIdentity().getIdType());
        Assertions.assertTrue(orderModel.getField("orderNo").isWritable());
        Assertions.assertEquals(OrderEntity.class, model.resolveEntityType("order"));
        Assertions.assertEquals(OrderEntity.class, model.resolveEntityType(OrderEntity.class.getName()));

        CrudRuntimeRelationModel relation = model.getRelations().get(0);
        Assertions.assertEquals(OrderEntity.class, relation.getFromEntity());
        Assertions.assertEquals(OrderItemEntity.class, relation.getToEntity());
        Assertions.assertEquals("items", relation.getRelationField());
        Assertions.assertEquals(RelationCardinality.ONE_TO_MANY, relation.getCardinality());
    }

    @Test
    void runtime_model_collections_should_be_immutable_and_relation_edges_should_be_copied() {
        CrudRuntimeModel model = CrudRuntimeModel.from(
            Collections.singletonList(entityMeta(OrderEntity.class, "order", "t_order", EntityIdPolicy.EXPLICIT)),
            Collections.singletonList(relationEdge())
        );

        Assertions.assertThrows(UnsupportedOperationException.class, () -> model.getEntities().clear());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> model.getEntity(OrderEntity.class).getFields().clear());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> model.getRelations().clear());

        RelationEdge exported = model.relationEdges().get(0);
        exported.setRelationField("changed");
        Assertions.assertEquals("items", model.getRelations().get(0).getRelationField());
    }

    @Test
    void runtime_model_should_fail_fast_for_duplicate_resource_code() {
        EntityMeta order = entityMeta(OrderEntity.class, "order", "t_order", EntityIdPolicy.EXPLICIT);
        EntityMeta duplicate = entityMeta(OrderItemEntity.class, "order", "t_order_item", EntityIdPolicy.EXPLICIT);

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> CrudRuntimeModel.from(Arrays.asList(order, duplicate), Collections.<RelationEdge>emptyList())
        );

        Assertions.assertTrue(ex.getMessage().contains("资源编码或别名重复"));
    }

    private static EntityMeta entityMeta(Class<?> entityType, String resourceCode, String table, EntityIdPolicy idPolicy) {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("orderNo", new EntityFieldMeta("orderNo", String.class, "order_no", true, false, true, true));
        return new EntityMeta(
            entityType,
            new ResourceDescriptor(entityType, resourceCode, "test-service", Collections.<String>emptyList()),
            table,
            "id",
            idPolicy,
            null,
            fields
        );
    }

    private static RelationEdge relationEdge() {
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(OrderEntity.class);
        edge.setToEntity(OrderItemEntity.class);
        edge.setRelationField("items");
        edge.setFromField("id");
        edge.setToField("orderId");
        edge.setScope(RelationScope.LOCAL_DB);
        edge.setJoinKind(JoinType.LEFT);
        edge.setCardinality(RelationCardinality.ONE_TO_MANY);
        return edge;
    }

    private static final class OrderEntity {
    }

    private static final class OrderItemEntity {
    }
}
