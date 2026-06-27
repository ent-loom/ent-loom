package com.entloom.meta.adapter.crud;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.meta.adapter.crud.model.CrudRuntimeProperties;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.diagnostic.MetaDiagnostic;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticException;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticLevel;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.enums.EntFieldKind;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaCrudAdapterStaticFixtureTest {

    @Test
    void order_static_fixture_should_register_entity_fields_relations_and_diagnostics() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(Order.class, OrderItem.class, Customer.class));
        CrudRuntimeModelBackedEntityMetaRegistry registry = new CrudRuntimeModelBackedEntityMetaRegistry(adapter.runtimeModel());

        EntityMeta orderMeta = registry.getEntityMeta(Order.class);
        Assertions.assertEquals("order", orderMeta.getEntityName());
        Assertions.assertEquals("sales_order", orderMeta.getTable());
        Assertions.assertEquals("order_no", orderMeta.resolveColumn("orderNo"));
        Assertions.assertEquals("customer_id", orderMeta.resolveColumn("customerId"));
        Assertions.assertSame(Order.class, registry.resolveEntityType("order"));
        Assertions.assertEquals("customerId", registry.resolveFieldByColumn(Order.class, "customer_id"));

        Collection<RelationEdge> edges = registry.getRelationGraph(Order.class).getEdges();
        Assertions.assertEquals(2, edges.size());
        RelationEdge customerEdge = findEdge(edges, Customer.class);
        Assertions.assertSame(Order.class, customerEdge.getFromEntity());
        Assertions.assertEquals("customerId", customerEdge.getRelationField());
        Assertions.assertEquals("customerId", customerEdge.getFromField());
        Assertions.assertEquals("id", customerEdge.getToField());
        Assertions.assertEquals(JoinType.INNER, customerEdge.getJoinKind());

        RelationEdge itemEdge = findEdge(edges, OrderItem.class);
        Assertions.assertSame(Order.class, itemEdge.getFromEntity());
        Assertions.assertEquals("items", itemEdge.getRelationField());
        Assertions.assertEquals("id", itemEdge.getFromField());
        Assertions.assertEquals("orderId", itemEdge.getToField());
        Assertions.assertEquals(RelationCardinality.ONE_TO_MANY, itemEdge.getCardinality());

        Assertions.assertFalse(hasError(adapter.diagnostics()));
        Assertions.assertFalse(adapter.diagnostics().isEmpty());
    }

    @Test
    void adapter_should_reject_explicit_target_class_outside_registered_entities() {
        MetaDiagnosticException exception = Assertions.assertThrows(
            MetaDiagnosticException.class,
            () -> new MetaCrudAdapter(Collections.<Class<?>>singletonList(DanglingOrder.class))
        );

        Assertions.assertTrue(hasDiagnostic(
            exception.diagnostics(),
            MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND,
            CrudRuntimeProperties.TARGET_CLASS
        ));
    }

    private RelationEdge findEdge(Collection<RelationEdge> edges, Class<?> targetType) {
        for (RelationEdge edge : edges) {
            if (targetType.equals(edge.getToEntity())) {
                return edge;
            }
        }
        Assertions.fail("Missing edge to " + targetType.getName());
        return null;
    }

    private boolean hasError(List<MetaDiagnostic> diagnostics) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.level() == MetaDiagnosticLevel.ERROR) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDiagnostic(List<MetaDiagnostic> diagnostics, MetaDiagnosticCode code, String property) {
        for (MetaDiagnostic diagnostic : diagnostics) {
            if (diagnostic.code() == code && property.equals(diagnostic.property())) {
                return true;
            }
        }
        return false;
    }

    @EntEntity(entity = "order", service = "order-service")
    @EntCrudEntity(name = "order", table = "sales_order")
    private static final class Order {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.TEXT)
        private String orderNo;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "customer")
        @EntCrudField(targetClass = Customer.class, joinType = JoinType.INNER)
        private Long customerId;

        @EntRelation(targetEntity = "order_item", targetField = "orderId", cardinality = com.entloom.meta.enums.RelationCardinality.ONE_TO_MANY)
        @EntCrudField(targetClass = OrderItem.class, targetField = "orderId", cardinality = RelationCardinality.ONE_TO_MANY)
        private List<OrderItem> items;
    }

    @EntEntity(entity = "order_item", service = "order-service")
    @EntCrudEntity(name = "order_item", table = "sales_order_item")
    private static final class OrderItem {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        private Long orderId;

        @EntField(EntFieldKind.TEXT)
        private String sku;
    }

    @EntEntity(entity = "customer", service = "customer-service")
    @EntCrudEntity(name = "customer", table = "crm_customer")
    private static final class Customer {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.TEXT)
        private String name;
    }

    @EntCrudEntity(name = "dangling_order")
    private static final class DanglingOrder {
        private Long id;

        @EntCrudField(targetClass = DanglingCustomer.class)
        private Long customerId;
    }

    @EntCrudEntity(name = "dangling_customer")
    private static final class DanglingCustomer {
        private Long id;
    }
}
