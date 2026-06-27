package com.entloom.meta.adapter.crud;

import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import com.entloom.meta.enums.EntFieldKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaCrudAdapterRelationDirectionTest {

    @Test
    void many_to_one_edge_should_keep_declaring_entity_as_from_side() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(DirectionOrder.class, DirectionCustomer.class));

        RelationEdge edge = onlyEdge(adapter.runtimeModel().relationEdges());

        Assertions.assertSame(DirectionOrder.class, edge.getFromEntity());
        Assertions.assertSame(DirectionCustomer.class, edge.getToEntity());
        Assertions.assertEquals("customerId", edge.getRelationField());
        Assertions.assertEquals("customerId", edge.getFromField());
        Assertions.assertEquals("id", edge.getToField());
        Assertions.assertEquals(RelationCardinality.MANY_TO_ONE, edge.getCardinality());
    }

    @Test
    void one_to_many_edge_should_preserve_declaring_direction_and_target_owner_side() {
        EntRelationDescriptor relation = new ReflectiveEntMetaParser()
            .parse(DirectionCustomerAggregate.class)
            .relations()
            .get(0);
        Assertions.assertEquals(RelationOwnerSide.TARGET_ENTITY, relation.ownerSide());
        Assertions.assertTrue(relation.sourceFieldInferred());

        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(DirectionCustomerAggregate.class, DirectionOrderLine.class));

        RelationEdge edge = onlyEdge(adapter.runtimeModel().relationEdges());

        Assertions.assertSame(DirectionCustomerAggregate.class, edge.getFromEntity());
        Assertions.assertSame(DirectionOrderLine.class, edge.getToEntity());
        Assertions.assertEquals("lines", edge.getRelationField());
        Assertions.assertEquals("id", edge.getFromField());
        Assertions.assertEquals("customerId", edge.getToField());
        Assertions.assertEquals(RelationCardinality.ONE_TO_MANY, edge.getCardinality());
    }

    @Test
    void many_to_many_edge_should_keep_semantic_declaring_direction() {
        EntRelationDescriptor relation = new ReflectiveEntMetaParser()
            .parse(DirectionArticle.class)
            .relations()
            .get(0);
        Assertions.assertEquals(RelationOwnerSide.UNKNOWN, relation.ownerSide());

        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(DirectionArticle.class, DirectionTag.class));

        RelationEdge edge = onlyEdge(adapter.runtimeModel().relationEdges());

        Assertions.assertSame(DirectionArticle.class, edge.getFromEntity());
        Assertions.assertSame(DirectionTag.class, edge.getToEntity());
        Assertions.assertEquals("tags", edge.getRelationField());
        Assertions.assertEquals("tags", edge.getFromField());
        Assertions.assertEquals("id", edge.getToField());
        Assertions.assertEquals(RelationCardinality.MANY_TO_MANY, edge.getCardinality());
    }

    private RelationEdge onlyEdge(Collection<RelationEdge> edges) {
        List<RelationEdge> items = new ArrayList<RelationEdge>(edges);
        Assertions.assertEquals(1, items.size());
        return items.get(0);
    }

    @EntEntity(entity = "direction_order")
    private static final class DirectionOrder {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        @EntRelation(targetEntity = "direction_customer")
        private Long customerId;
    }

    @EntEntity(entity = "direction_customer")
    private static final class DirectionCustomer {
        @EntField(EntFieldKind.ID)
        private Long id;
    }

    @EntEntity(entity = "direction_customer_aggregate")
    private static final class DirectionCustomerAggregate {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntRelation(
            targetEntity = "direction_order_line",
            targetField = "customerId",
            cardinality = com.entloom.meta.enums.RelationCardinality.ONE_TO_MANY
        )
        private List<DirectionOrderLine> lines;
    }

    @EntEntity(entity = "direction_order_line")
    private static final class DirectionOrderLine {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntField(EntFieldKind.REF_ID)
        private Long customerId;
    }

    @EntEntity(entity = "direction_article")
    private static final class DirectionArticle {
        @EntField(EntFieldKind.ID)
        private Long id;

        @EntRelation(
            targetEntity = "direction_tag",
            cardinality = com.entloom.meta.enums.RelationCardinality.MANY_TO_MANY
        )
        private List<DirectionTag> tags;
    }

    @EntEntity(entity = "direction_tag")
    private static final class DirectionTag {
        @EntField(EntFieldKind.ID)
        private Long id;
    }
}
