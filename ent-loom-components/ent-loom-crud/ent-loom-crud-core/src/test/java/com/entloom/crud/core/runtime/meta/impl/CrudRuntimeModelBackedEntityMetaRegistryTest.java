package com.entloom.crud.core.runtime.meta.impl;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.runtime.model.CrudRuntimeModel;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CrudRuntimeModelBackedEntityMetaRegistryTest {
    @Test
    void model_backed_registry_should_expose_entity_meta_resource_lookup_and_column_lookup() {
        CrudRuntimeModelBackedEntityMetaRegistry registry = registry();

        EntityMeta orderMeta = registry.getEntityMeta(TestOrder.class);

        Assertions.assertEquals("test_order", orderMeta.getTable());
        Assertions.assertEquals(EntityIdPolicy.GENERATED, orderMeta.getIdPolicy());
        Assertions.assertSame(TestOrder.class, registry.resolveEntityType("order"));
        Assertions.assertSame(TestOrder.class, registry.resolveEntityType("OrderAlias"));
        Assertions.assertEquals("orderNo", registry.resolveFieldByColumn(TestOrder.class, "order_no"));
    }

    @Test
    void model_backed_registry_should_preserve_field_export_metadata() {
        CrudRuntimeModelBackedEntityMetaRegistry registry = registry();

        EntityFieldMeta fieldMeta = registry.getEntityMeta(TestOrder.class).resolveFieldMeta("orderNo");

        Assertions.assertEquals(Boolean.TRUE, fieldMeta.getExportable());
        Assertions.assertEquals(Boolean.FALSE, fieldMeta.getExportDefaultVisible());
        Assertions.assertEquals("订单号", fieldMeta.getExportLabel());
        Assertions.assertEquals("text", fieldMeta.getExportFormat());
        Assertions.assertEquals("order_status", fieldMeta.getDictionaryCode());
        Assertions.assertEquals("orderName", fieldMeta.getDisplayField());
    }

    @Test
    void model_backed_registry_should_precompute_immutable_reachable_relation_graph() {
        CrudRuntimeModelBackedEntityMetaRegistry registry = registry();

        Assertions.assertSame(registry.getRelationGraph(TestOrder.class), registry.getRelationGraph(TestOrder.class));

        RelationEdge edge = registry.getRelationGraph(TestOrder.class).getEdges().get(0);
        Assertions.assertSame(TestOrder.class, edge.getFromEntity());
        Assertions.assertSame(TestOrderItem.class, edge.getToEntity());
        Assertions.assertEquals("items", edge.getRelationField());

        Assertions.assertThrows(UnsupportedOperationException.class, () -> edge.setRelationField("changed"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> registry.getRelationGraph(TestOrder.class).getEdges().clear());
        Assertions.assertEquals("items", registry.getRelationGraph(TestOrder.class).getEdges().get(0).getRelationField());
    }

    @Test
    void model_backed_registry_should_fail_fast_when_relation_target_is_not_registered() {
        RelationEdge edge = relationEdge();
        edge.setToEntity(TestUnregistered.class);

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new CrudRuntimeModelBackedEntityMetaRegistry(
                CrudRuntimeModel.from(Arrays.asList(orderMeta(), itemMeta()), Collections.singletonList(edge))
            )
        );

        Assertions.assertTrue(ex.getMessage().contains("关系目标实体未注册"));
    }

    @Test
    void model_backed_registry_should_fail_fast_when_relation_field_is_not_registered() {
        RelationEdge edge = relationEdge();
        edge.setToField("missingOrderId");

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new CrudRuntimeModelBackedEntityMetaRegistry(
                CrudRuntimeModel.from(Arrays.asList(orderMeta(), itemMeta()), Collections.singletonList(edge))
            )
        );

        Assertions.assertTrue(ex.getMessage().contains("关系目标字段不存在"));
    }

    @Test
    void model_backed_registry_should_fail_fast_when_model_has_no_entities() {
        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new CrudRuntimeModelBackedEntityMetaRegistry(
                new CrudRuntimeModel(Collections.emptyList(), Collections.emptyList())
            )
        );

        Assertions.assertTrue(ex.getMessage().contains("未提供任何实体元数据"));
    }

    private static CrudRuntimeModelBackedEntityMetaRegistry registry() {
        return new CrudRuntimeModelBackedEntityMetaRegistry(
            CrudRuntimeModel.from(Arrays.asList(orderMeta(), itemMeta()), Collections.singletonList(relationEdge()))
        );
    }

    private static EntityMeta orderMeta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put(
            "orderNo",
            new EntityFieldMeta(
                "orderNo",
                String.class,
                "order_no",
                true,
                false,
                true,
                true,
                true,
                false,
                false,
                Boolean.TRUE,
                Boolean.FALSE,
                "订单号",
                "text",
                "order_status",
                "orderName"
            )
        );
        return new EntityMeta(
            TestOrder.class,
            new ResourceDescriptor(TestOrder.class, "order", "test-service", Collections.singleton("OrderAlias")),
            "test_order",
            "id",
            EntityIdPolicy.GENERATED,
            null,
            fields
        );
    }

    private static EntityMeta itemMeta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", new EntityFieldMeta("id", Long.class, "id", false, false, true, true));
        fields.put("orderId", new EntityFieldMeta("orderId", Long.class, "order_id", false, false, true, true));
        return new EntityMeta(
            TestOrderItem.class,
            new ResourceDescriptor(TestOrderItem.class, "orderItem", "test-service", Collections.<String>emptyList()),
            "test_order_item",
            "id",
            EntityIdPolicy.EXPLICIT,
            null,
            fields
        );
    }

    private static RelationEdge relationEdge() {
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(TestOrder.class);
        edge.setToEntity(TestOrderItem.class);
        edge.setRelationField("items");
        edge.setFromField("id");
        edge.setToField("orderId");
        edge.setScope(RelationScope.LOCAL_DB);
        edge.setJoinKind(JoinType.LEFT);
        edge.setCardinality(RelationCardinality.ONE_TO_MANY);
        return edge;
    }

    private static final class TestOrder {
    }

    private static final class TestOrderItem {
    }

    private static final class TestUnregistered {
    }
}
