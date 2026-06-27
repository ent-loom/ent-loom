package com.entloom.crud.core.util;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RouteKeyFactoryTest {
    @Test
    void should_build_query_route_from_root_when_entity_classes_empty() {
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(OrderEntity.class)
            .op(QueryOperation.PAGE)
            .scene(" Order.Page ")
            .build();

        CrudRouteKey route = RouteKeyFactory.buildQueryRoute(spec);

        Assertions.assertEquals(Arrays.asList(OrderEntity.class.getName()), route.getEntityTypeNames());
        Assertions.assertEquals("PAGE", route.getOperation());
        Assertions.assertEquals("order.page", route.getScene());
        Assertions.assertEquals("QUERY/PAGE", route.getOperationKey().toString());
        Assertions.assertEquals(OrderEntity.class.getName() + "|QUERY/PAGE|order.page", route.toString());
    }

    @Test
    void should_deduplicate_entity_classes_but_keep_root_first_order() {
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderEntity.class, OrderItemEntity.class, OrderItemEntity.class))
            .op(CommandOperation.CREATE)
            .build();

        CrudRouteKey route = RouteKeyFactory.buildCommandRoute(spec);

        Assertions.assertEquals(
            Arrays.asList(OrderEntity.class.getName(), OrderItemEntity.class.getName()),
            route.getEntityTypeNames()
        );
        Assertions.assertEquals(
            OrderEntity.class.getName() + ">" + OrderItemEntity.class.getName() + "|COMMAND/CREATE",
            route.toString()
        );
    }

    @Test
    void should_reject_missing_root_type() {
        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .op(QueryOperation.PAGE)
            .build();

        Assertions.assertThrows(ValidationException.class, () -> RouteKeyFactory.buildQueryRoute(spec));
    }

    @Test
    void should_reject_entity_classes_when_first_item_is_not_root() {
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(OrderEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderItemEntity.class, OrderEntity.class))
            .op(CommandOperation.CREATE)
            .build();

        Assertions.assertThrows(ValidationException.class, () -> RouteKeyFactory.buildCommandRoute(spec));
    }

    @Test
    void should_normalize_scene_to_lowercase_trimmed_value() {
        Assertions.assertEquals("", RouteKeyFactory.normalizeScene(null));
        Assertions.assertEquals("", RouteKeyFactory.normalizeScene("  "));
        Assertions.assertEquals("order.place", RouteKeyFactory.normalizeScene(" Order.Place "));
    }

    private static final class OrderEntity {
    }

    private static final class OrderItemEntity {
    }
}
