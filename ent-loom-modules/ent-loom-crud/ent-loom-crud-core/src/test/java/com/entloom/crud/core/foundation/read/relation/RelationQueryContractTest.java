package com.entloom.crud.core.foundation.read.relation;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.enums.RelationScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RelationQueryContractTest {
    @Test
    void path_resolver_should_resolve_explicit_expand_relation() {
        RelationEdge edge = edge(OrderEntity.class, OrderItemEntity.class, "items", RelationScope.LOCAL_DB);
        RelationGraph graph = RelationGraph.of(Collections.singletonList(edge));

        QuerySpec<OrderEntity> spec = QuerySpec.<OrderEntity>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderEntity.class, OrderItemEntity.class))
            .expandRelations(Collections.singletonList("items"))
            .build();

        RelationQueryModel model = new PathResolver().resolve(spec, graph);

        Assertions.assertEquals(Collections.singletonList("items"), model.getRequestedRelations());
        Assertions.assertEquals(1, model.getExpandEdges().size());
        Assertions.assertEquals(edge.getRelationField(), model.getExpandEdges().get(0).getRelationField());
    }

    @Test
    void validator_should_reject_remote_relation_for_default_query() {
        RelationQueryModel model = new RelationQueryModel(
            Collections.singletonList("items"),
            Collections.singletonList(edge(OrderEntity.class, RemoteItemEntity.class, "items", RelationScope.REMOTE_SERVICE))
        );

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new RelationQueryValidator().validate(QuerySpec.builder().rootType(OrderEntity.class).build(), model)
        );
        Assertions.assertTrue(ex.getMessage().contains("LOCAL_DB"));
    }

    @Test
    void validator_should_allow_remote_relation_when_loader_is_explicitly_available() {
        RelationEdge edge = edge(OrderEntity.class, RemoteItemEntity.class, "remoteItems", RelationScope.REMOTE_SERVICE);
        RelationQueryModel model = new RelationQueryModel(
            Collections.singletonList("remoteItems"),
            Collections.singletonList(edge)
        );
        RelationLoader loader = new RelationLoader() {
            @Override
            public boolean supports(RelationEdge current) {
                return current == edge;
            }

            @Override
            public List<Object> load(RelationLoadRequest request) {
                return new ArrayList<Object>();
            }
        };

        new RelationQueryValidator(
            new RelationQueryPolicy(1, 4, false, true),
            new RelationLoaderRegistry(Collections.singletonList(loader))
        ).validate(QuerySpec.builder().rootType(OrderEntity.class).build(), model);
    }

    @Test
    void validator_should_reject_depth_over_policy() {
        RelationQueryModel model = new RelationQueryModel(
            Arrays.asList("items", "remoteItems"),
            Arrays.asList(
                edge(OrderEntity.class, OrderItemEntity.class, "items", RelationScope.LOCAL_DB),
                edge(OrderItemEntity.class, RemoteItemEntity.class, "remoteItems", RelationScope.LOCAL_DB)
            )
        );

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new RelationQueryValidator(new RelationQueryPolicy(1, 4, false, false), null)
                .validate(QuerySpec.builder().rootType(OrderEntity.class).build(), model)
        );
        Assertions.assertTrue(ex.getMessage().contains("深度"));
    }

    @Test
    void validator_should_reject_cycle_by_default() {
        RelationQueryModel model = new RelationQueryModel(
            Arrays.asList("items", "order"),
            Arrays.asList(
                edge(OrderEntity.class, OrderItemEntity.class, "items", RelationScope.LOCAL_DB),
                edge(OrderItemEntity.class, OrderEntity.class, "order", RelationScope.LOCAL_DB)
            )
        );

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new RelationQueryValidator(new RelationQueryPolicy(4, 4, false, false), null)
                .validate(QuerySpec.builder().rootType(OrderEntity.class).build(), model)
        );
        Assertions.assertTrue(ex.getMessage().contains("循环"));
    }

    @Test
    void path_resolver_should_infer_collection_edge_from_reverse_graph_without_meta_registry() {
        RelationEdge reverse = edge(OrderItemEntity.class, OrderEntity.class, "orderId", RelationScope.LOCAL_DB);
        reverse.setFromField("orderId");
        reverse.setToField("id");
        RelationGraph graph = RelationGraph.of(Collections.singletonList(reverse));

        QuerySpec<OrderEntity> spec = QuerySpec.<OrderEntity>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderEntity.class, OrderItemEntity.class))
            .build();

        RelationQueryModel model = new PathResolver().resolve(spec, graph);

        Assertions.assertEquals(1, model.getExpandEdges().size());
        RelationEdge inferred = model.getExpandEdges().get(0);
        Assertions.assertEquals(OrderEntity.class, inferred.getFromEntity());
        Assertions.assertEquals(OrderItemEntity.class, inferred.getToEntity());
        Assertions.assertEquals("items", inferred.getRelationField());
        Assertions.assertEquals("id", inferred.getFromField());
        Assertions.assertEquals("orderId", inferred.getToField());
    }

    @Test
    void path_resolver_should_prefer_earlier_source_entity_when_target_has_multiple_sources() {
        RelationEdge rootToMiddle = edge(OrderEntity.class, OrderItemEntity.class, "items", RelationScope.LOCAL_DB);
        RelationEdge rootToTarget = edge(OrderEntity.class, RemoteItemEntity.class, "rootTarget", RelationScope.LOCAL_DB);
        RelationEdge middleToTarget = edge(
            OrderItemEntity.class, RemoteItemEntity.class, "middleTarget", RelationScope.LOCAL_DB
        );
        RelationGraph graph = RelationGraph.of(Arrays.asList(rootToMiddle, rootToTarget, middleToTarget));

        QuerySpec<OrderEntity> spec = QuerySpec.<OrderEntity>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(
                OrderEntity.class,
                OrderItemEntity.class,
                RemoteItemEntity.class
            ))
            .build();

        RelationQueryModel model = new PathResolver().resolve(spec, graph);

        Assertions.assertEquals(2, model.getExpandEdges().size());
        Assertions.assertEquals("rootTarget", model.getExpandEdges().get(1).getRelationField());
    }

    @Test
    void path_resolver_should_keep_first_alias_when_join_semantics_are_identical() {
        RelationEdge primary = edge(OrderEntity.class, RemoteItemEntity.class, "targetId", RelationScope.LOCAL_DB);
        RelationEdge alias = edge(OrderEntity.class, RemoteItemEntity.class, "target", RelationScope.LOCAL_DB);
        RelationGraph graph = RelationGraph.of(Arrays.asList(primary, alias));

        QuerySpec<OrderEntity> spec = QuerySpec.<OrderEntity>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderEntity.class, RemoteItemEntity.class))
            .build();

        RelationQueryModel model = new PathResolver().resolve(spec, graph);

        Assertions.assertEquals(1, model.getExpandEdges().size());
        Assertions.assertEquals("targetId", model.getExpandEdges().get(0).getRelationField());
    }

    @Test
    void path_resolver_should_reject_distinct_relations_from_same_source() {
        RelationEdge currentTarget = edge(
            OrderEntity.class, RemoteItemEntity.class, "currentTarget", RelationScope.LOCAL_DB
        );
        RelationEdge originalTarget = edge(
            OrderEntity.class, RemoteItemEntity.class, "originalTarget", RelationScope.LOCAL_DB
        );
        originalTarget.setFromField("originalTargetId");
        RelationGraph graph = RelationGraph.of(Arrays.asList(currentTarget, originalTarget));

        QuerySpec<OrderEntity> spec = QuerySpec.<OrderEntity>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderEntity.class, RemoteItemEntity.class))
            .build();

        CrudException ex = Assertions.assertThrows(
            CrudException.class,
            () -> new PathResolver().resolve(spec, graph)
        );

        Assertions.assertEquals(CrudErrorCode.ENTITY_SCOPE_ILLEGAL, ex.getErrorCode());
        Assertions.assertTrue(ex.getMessage().contains("来源实体 OrderEntity 存在多条关联"));
    }

    private static RelationEdge edge(Class<?> from, Class<?> to, String relationField, RelationScope scope) {
        RelationEdge edge = new RelationEdge();
        edge.setFromEntity(from);
        edge.setToEntity(to);
        edge.setRelationField(relationField);
        edge.setFromField("id");
        edge.setToField("orderId");
        edge.setScope(scope);
        return edge;
    }

    static class OrderEntity {
        private List<OrderItemEntity> items;
    }

    static class OrderItemEntity {
    }

    static class RemoteItemEntity {
    }
}
