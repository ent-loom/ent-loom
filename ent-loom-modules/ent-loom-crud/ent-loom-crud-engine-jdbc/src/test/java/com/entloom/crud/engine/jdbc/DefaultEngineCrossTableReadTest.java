package com.entloom.crud.engine.jdbc;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultEngineCrossTableReadTest extends EngineJdbcTestSupport {
    @Test
    void read_orders_with_items_should_use_root_first_and_batch_expand() {
        jdbcTemplate.update("insert into t_order(id, order_no, is_deleted) values (?,?,?)", 3001L, "ORD-CROSS", 0);
        jdbcTemplate.update("insert into t_order_item(id, order_id, sku_code, quantity, is_deleted) values (?,?,?,?,?)", 1L, 3001L, "SKU-1", 2, 0);
        jdbcTemplate.update("insert into t_order_item(id, order_id, sku_code, quantity, is_deleted) values (?,?,?,?,?)", 2L, 3001L, "SKU-2", 1, 0);

        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .scene(null)
            .rootType(OrderTestEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class))
            .subject(testSubject())
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.PAGE)
            .page(new PageRequest(1, 10))
            .filters(Collections.singletonList(new QueryFilter("isDeleted", FilterOperator.EQ, 0)))
            .build();

        com.entloom.crud.api.model.PageResult<OrderTestEntity> page = queryGateway.page(spec);
        Assertions.assertEquals(1, page.getItems().size());
        Assertions.assertNotNull(page.getItems().get(0).getItems());
        Assertions.assertEquals(2, page.getItems().get(0).getItems().size());
    }
}
