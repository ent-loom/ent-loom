package com.entloom.crud.engine.jdbc;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.adapter.AccessEntryResolver;
import com.entloom.crud.core.capability.query.spec.ExistsRelationFilter;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.NotFoundException;
import com.entloom.crud.core.governance.scope.CrudDataScopeContributor;
import com.entloom.crud.core.governance.scope.ReadVisibilityContributor;
import com.entloom.crud.engine.jdbc.test.entity.OrderItemTestEntity;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import com.entloom.crud.enums.QueryStrategy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadVisibilityGatewayTest extends EngineJdbcTestSupport {
    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into t_order values (1,'VISIBLE',10,'tenant-a',0),(2,'HIDDEN',10,'tenant-a',0)");
        jdbcTemplate.update("insert into t_order_item values (11,1,1,'tenant-a','VISIBLE-SKU',1,0),(12,1,1,'tenant-a','HIDDEN-SKU',2,0)");
    }

    @Override
    protected List<CrudDataScopeContributor> createDataScopeContributors() {
        Map<Class<?>, Map<String, Map<String, Object>>> visibility = new LinkedHashMap<>();
        visibility.put(OrderTestEntity.class, entries("orderNo", "VISIBLE"));
        visibility.put(OrderItemTestEntity.class, entries("quantity", 1));
        return Collections.singletonList(new ReadVisibilityContributor(metaRegistry, visibility));
    }

    private Map<String, Map<String, Object>> entries(String field, Object value) {
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        entries.put("consumer", Collections.singletonMap(field, value));
        entries.put("admin", Collections.emptyMap());
        return entries;
    }

    @Test
    void 默认分页计数详情及显式反向筛选均无法绕过规则() {
        QuerySpec<OrderTestEntity> spec = spec("consumer");
        assertEquals(1, queryGateway.list(spec).size());
        assertEquals(Long.valueOf(1), queryGateway.list(spec).get(0).getId());
        assertEquals(1L, queryGateway.page(spec.toBuilder().op(QueryOperation.PAGE).page(new PageRequest(1, 1)).build()).getTotal());
        assertNull(queryGateway.findOne(spec.toBuilder().op(QueryOperation.FIND_ONE).limit(null).filters(Collections.singletonList(
            new QueryFilter("id", FilterOperator.EQ, 2L))).build()));
        assertThrows(NotFoundException.class, () -> queryGateway.detail(spec.toBuilder().op(QueryOperation.DETAIL).filters(Collections.singletonList(
            new QueryFilter("id", FilterOperator.EQ, 2L))).build()));
        assertTrue(queryGateway.list(spec.toBuilder().filters(Collections.singletonList(
            new QueryFilter("orderNo", FilterOperator.EQ, "HIDDEN"))).build()).isEmpty());
        assertEquals(2, queryGateway.list(spec("admin")).size());
        assertThrows(DataScopeDeniedException.class, () -> queryGateway.list(spec("unknown")));
    }

    @Test
    void 关联展开和Exists按目标实体规则执行且不污染根实体() {
        OrderTestEntity order = queryGateway.detail(spec("consumer").toBuilder().op(QueryOperation.DETAIL)
            .expandRelations(Collections.singletonList("items"))
            .filters(Collections.singletonList(new QueryFilter("id", FilterOperator.EQ, 1L))).build());
        assertEquals(1, order.getItems().size());
        assertEquals("VISIBLE-SKU", order.getItems().get(0).getSkuCode());
        QuerySpec<OrderTestEntity> exists = spec("consumer").toBuilder().strategy(QueryStrategy.EXISTS)
            .existsRelationFilter(new ExistsRelationFilter("items",
                Collections.singletonList(new QueryFilter("skuCode", FilterOperator.EQ, "HIDDEN-SKU")))).build();
        assertTrue(queryGateway.list(exists).isEmpty());
        assertEquals(1, queryGateway.list(exists.toBuilder()
            .attributes(Collections.<String, Object>singletonMap(AccessEntryResolver.ATTRIBUTE_KEY, "admin")).build()).size());
    }

    private QuerySpec<OrderTestEntity> spec(String entry) {
        return QuerySpec.<OrderTestEntity>builder().rootType(OrderTestEntity.class)
            .resultType(OrderTestEntity.class).subject(testSubject()).op(QueryOperation.LIST).limit(10)
            .attributes(Collections.<String, Object>singletonMap(AccessEntryResolver.ATTRIBUTE_KEY, entry)).build();
    }
}
