package com.entloom.meta.adapter.crud;

import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticCode;
import com.entloom.meta.contract.diagnostic.MetaDiagnosticException;
import com.entloom.meta.contract.value.MetaValueSource;
import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.core.parser.ReflectiveEntMetaParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetaCrudAdapterRelationDirectionTest {

    @Test
    void 类引用仍要求目标实体显式注册() {
        MetaDiagnosticException exception = Assertions.assertThrows(MetaDiagnosticException.class,
            () -> new MetaCrudAdapter(Arrays.<Class<?>>asList(DirectionOrder.class)));

        Assertions.assertTrue(exception.diagnostics().stream().anyMatch(diagnostic ->
            diagnostic.code() == MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND));
    }

    @Test
    void 同标识的其他实体不能替代类引用目标() {
        MetaDiagnosticException exception = Assertions.assertThrows(MetaDiagnosticException.class,
            () -> new MetaCrudAdapter(Arrays.<Class<?>>asList(DirectionOrder.class, CustomerAlias.class)));

        Assertions.assertTrue(exception.diagnostics().stream().anyMatch(diagnostic ->
            diagnostic.code() == MetaDiagnosticCode.RELATION_TARGET_ENTITY_NOT_FOUND));
    }

    @EntEntity(entity = "direction_customer")
    private static class CustomerAlias {
        Long id;
    }

    @Test
    void 唯一反向引用补全集合映射且不改变Meta关系事实() {
        for (List<Class<?>> types : Arrays.asList(Arrays.<Class<?>>asList(SimpleParent.class, SimpleChild.class),
            Arrays.<Class<?>>asList(SimpleChild.class, SimpleParent.class))) {
            MetaCrudAdapter adapter = new MetaCrudAdapter(types);
            List<RelationEdge> edges = adapter.runtimeModel().relationEdges();
            Assertions.assertEquals(2, edges.size());
            RelationEdge collection = edges.stream().filter(edge -> edge.getFromEntity() == SimpleParent.class)
                .findFirst().orElseThrow(AssertionError::new);
            Assertions.assertEquals("lines", collection.getRelationField());
            Assertions.assertEquals("id", collection.getFromField());
            Assertions.assertEquals("parentId", collection.getToField());
            Assertions.assertEquals(RelationCardinality.ONE_TO_MANY, collection.getCardinality());
            Assertions.assertNull(adapter.runtimeModel().getEntity(SimpleParent.class).getField("lines"));
            Assertions.assertTrue(new ReflectiveEntMetaParser().parse(SimpleParent.class).relations().isEmpty());
        }
    }

    @Test
    void 同一目标的多个反向引用不自动绑定父集合() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(MultiParent.class, MultiChild.class));
        Assertions.assertEquals(2, adapter.runtimeModel().relationEdges().size());
        Assertions.assertTrue(adapter.runtimeModel().relationEdges().stream()
            .allMatch(edge -> edge.getFromEntity() == MultiChild.class));
    }

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
    void 外键关系绑定继承的同名导航属性且保留列映射() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(NavigatedOrder.class, DirectionCustomer.class));

        RelationEdge edge = onlyEdge(adapter.runtimeModel().relationEdges());

        Assertions.assertEquals("customer", edge.getRelationField());
        Assertions.assertEquals("customerId", edge.getFromField());
        Assertions.assertEquals("id", edge.getToField());
        Assertions.assertEquals(RelationCardinality.MANY_TO_ONE, edge.getCardinality());
        Assertions.assertNotNull(adapter.runtimeModel().getEntity(NavigatedOrder.class).getField("customerId"));
        Assertions.assertNull(adapter.runtimeModel().getEntity(NavigatedOrder.class).getField("customer"));
        Assertions.assertTrue(adapter.diagnostics().stream().anyMatch(diagnostic ->
            diagnostic.code() == MetaDiagnosticCode.INFERRED_VALUE_USED
                && "relationField".equals(diagnostic.property())
                && diagnostic.source() == MetaValueSource.INFERRED
                && "crud.relation.navigation.id-suffix".equals(diagnostic.ruleId())));
    }

    @Test
    void 同一目标的多个外键按名称分别绑定且不生成反向集合关系() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(TwoCustomerOrder.class, DirectionCustomer.class));

        List<RelationEdge> edges = adapter.runtimeModel().relationEdges();

        Assertions.assertEquals(2, edges.size());
        Assertions.assertTrue(edges.stream().anyMatch(edge ->
            "buyer".equals(edge.getRelationField()) && "buyerId".equals(edge.getFromField())));
        Assertions.assertTrue(edges.stream().anyMatch(edge ->
            "seller".equals(edge.getRelationField()) && "sellerId".equals(edge.getFromField())));
        Assertions.assertTrue(edges.stream().allMatch(edge -> edge.getFromEntity() == TwoCustomerOrder.class));
    }

    @Test
    void 不按目标类型猜测不同名导航属性() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(UnmatchedOrder.class, DirectionCustomer.class));

        RelationEdge edge = onlyEdge(adapter.runtimeModel().relationEdges());

        Assertions.assertEquals("customerId", edge.getRelationField());
    }

    @Test
    void 导航属性上的显式非标准映射保持不变() {
        MetaCrudAdapter adapter = new MetaCrudAdapter(Arrays.<Class<?>>asList(ExplicitNavigationOrder.class, DirectionCustomer.class));

        RelationEdge edge = onlyEdge(adapter.runtimeModel().relationEdges());

        Assertions.assertEquals("buyer", edge.getRelationField());
        Assertions.assertEquals("customerId", edge.getFromField());
    }

    @Test
    void 同名导航属性类型错误时给出可定位诊断() {
        MetaDiagnosticException exception = Assertions.assertThrows(MetaDiagnosticException.class,
            () -> new MetaCrudAdapter(Arrays.<Class<?>>asList(WrongNavigationOrder.class, DirectionCustomer.class)));

        Assertions.assertTrue(exception.diagnostics().stream().anyMatch(diagnostic ->
            diagnostic.code() == MetaDiagnosticCode.RELATION_NAVIGATION_TYPE_MISMATCH
                && (WrongNavigationOrder.class.getName() + "#customer").equals(diagnostic.location())));
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

    @EntEntity(entity = "simpleParent")
    private static class SimpleParent {
        Long id;
        List<SimpleChild> lines;
    }

    @EntEntity
    private static class SimpleChild {
        Long id;
        @EntRelation(SimpleParent.class)
        Long parentId;
    }

    @EntEntity(entity = "multiParent")
    private static class MultiParent {
        Long id;
        List<MultiChild> lines;
    }

    @EntEntity
    private static class MultiChild {
        Long id;
        @EntRelation(MultiParent.class)
        Long primaryId;
        @EntRelation(MultiParent.class)
        Long secondaryId;
    }

    @EntEntity(entity = "direction_order")
    private static final class DirectionOrder {
        @EntField
        private Long id;

        @EntField
        @EntRelation(DirectionCustomer.class)
        private Long customerId;
    }

    @EntEntity(entity = "direction_customer")
    private static final class DirectionCustomer {
        @EntField
        private Long id;
    }

    private static class NavigatedOrderBase {
        private Long id;

        @EntRelation(DirectionCustomer.class)
        private Long customerId;

        private DirectionCustomer customer;
    }

    @EntEntity
    private static final class NavigatedOrder extends NavigatedOrderBase {
    }

    @EntEntity
    private static final class TwoCustomerOrder {
        private Long id;

        @EntRelation(DirectionCustomer.class)
        private Long buyerId;

        @EntRelation(DirectionCustomer.class)
        private Long sellerId;

        private DirectionCustomer buyer;
        private DirectionCustomer seller;
        private List<DirectionCustomer> customerList;
    }

    @EntEntity
    private static final class UnmatchedOrder {
        private Long id;

        @EntRelation(DirectionCustomer.class)
        private Long customerId;

        private DirectionCustomer buyer;
        private List<DirectionCustomer> customerList;
    }

    @EntEntity
    private static final class ExplicitNavigationOrder {
        private Long id;
        private Long customerId;

        @EntRelation(value = DirectionCustomer.class, sourceField = "customerId")
        private DirectionCustomer buyer;
    }

    @EntEntity
    private static final class WrongNavigationOrder {
        private Long id;

        @EntRelation(DirectionCustomer.class)
        private Long customerId;

        private DirectionTag customer;
    }

    @EntEntity(entity = "direction_customer_aggregate")
    private static final class DirectionCustomerAggregate {
        @EntField
        private Long id;

        @EntRelation(
            value = DirectionOrderLine.class,
            targetField = "customerId",
            cardinality = com.entloom.meta.enums.RelationCardinality.ONE_TO_MANY
        )
        private List<DirectionOrderLine> lines;
    }

    @EntEntity(entity = "direction_order_line")
    private static final class DirectionOrderLine {
        @EntField
        private Long id;

        @EntField
        private Long customerId;
    }

    @EntEntity(entity = "direction_article")
    private static final class DirectionArticle {
        @EntField
        private Long id;

        @EntRelation(
            value = DirectionTag.class,
            cardinality = com.entloom.meta.enums.RelationCardinality.MANY_TO_MANY
        )
        private List<DirectionTag> tags;
    }

    @EntEntity(entity = "direction_tag")
    private static final class DirectionTag {
        @EntField
        private Long id;
    }
}
