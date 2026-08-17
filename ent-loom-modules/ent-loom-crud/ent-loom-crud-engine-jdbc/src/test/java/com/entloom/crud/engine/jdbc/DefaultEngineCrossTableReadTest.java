package com.entloom.crud.engine.jdbc;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.engine.jdbc.query.JdbcQueryCompiler;
import com.entloom.crud.engine.jdbc.query.JdbcQueryExecutor;
import com.entloom.crud.engine.jdbc.query.RootFirstQueryPlanner;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.SqlIdentifierAllowlistValidator;
import com.entloom.crud.engine.jdbc.security.SqlParameterLimiter;
import com.entloom.crud.engine.jdbc.security.SqlSafetyGuard;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void relation_expand_should_apply_governance_scope_to_child_rows() {
        jdbcTemplate.update("insert into t_order(id, order_no, tenant_id, is_deleted) values (?,?,?,?)", 3002L, "ORD-SCOPE", "tenant-a", 0);
        jdbcTemplate.update("insert into t_order_item(id, order_id, tenant_id, sku_code, quantity, is_deleted) values (?,?,?,?,?,?)", 11L, 3002L, "tenant-a", "SKU-A", 1, 0);
        jdbcTemplate.update("insert into t_order_item(id, order_id, tenant_id, sku_code, quantity, is_deleted) values (?,?,?,?,?,?)", 12L, 3002L, "tenant-b", "SKU-B", 1, 0);

        Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
        dimensions.put("tenantId", "tenant-a");
        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .rootType(OrderTestEntity.class)
            .entityClasses(Arrays.<Class<?>>asList(OrderTestEntity.class, OrderItemTestEntity.class))
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(10)
            .governanceScope(CrudDataScope.scoped(dimensions))
            .build();

        SqlIdentifierAllowlistValidator whitelist = new SqlIdentifierAllowlistValidator(metaRegistry);
        SqlSafetyGuard safetyGuard = new SqlSafetyGuard(whitelist, new SqlParameterLimiter());
        JdbcGuardedSqlExecutor guardedExecutor = new JdbcGuardedSqlExecutor(
            jdbcTemplate,
            safetyGuard,
            new SqlExecutionLogger()
        );
        QueryPlan plan = new RootFirstQueryPlanner(metaRegistry).plan(
            spec,
            metaRegistry.getEntityMeta(OrderTestEntity.class),
            metaRegistry.getRelationGraph(OrderTestEntity.class)
        );
        CompiledQuery compiled = new JdbcQueryCompiler().compile(plan);
        List<OrderTestEntity> orders = new JdbcQueryExecutor(guardedExecutor, metaRegistry).executeList(compiled, OrderTestEntity.class);

        Assertions.assertEquals(1, orders.size());
        Assertions.assertNotNull(orders.get(0).getItems());
        Assertions.assertEquals(1, orders.get(0).getItems().size());
        Assertions.assertEquals("SKU-A", orders.get(0).getItems().get(0).getSkuCode());
    }
}
