package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.impl.CrudRuntimeModelBackedEntityMetaRegistry;
import com.entloom.crud.core.runtime.model.parser.CrudNativeRuntimeModelParser;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryPlan;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.enums.QueryStrategy;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcQueryCompilerContractTest {
    private EntityMetaRegistry metaRegistry;
    private EntityMeta orderMeta;
    private JdbcQueryCompiler compiler;

    @BeforeEach
    void setUp() {
        metaRegistry = new CrudRuntimeModelBackedEntityMetaRegistry(
            new CrudNativeRuntimeModelParser().parse(Collections.<Class<?>>singletonList(OrderTestEntity.class))
        );
        metaRegistry.validateOrThrow();
        orderMeta = metaRegistry.getEntityMeta(OrderTestEntity.class);
        compiler = new JdbcQueryCompiler();
    }

    @Test
    void should_compile_select_fields_to_explicit_projection_sql() {
        QuerySpec<OrderTestEntity> spec = QuerySpec.<OrderTestEntity>builder()
            .rootType(OrderTestEntity.class)
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(20)
            .selectFields(Arrays.asList("id", "orderNo"))
            .filters(Collections.singletonList(new QueryFilter("schoolId", FilterOperator.EQ, 10L)))
            .sorts(Collections.singletonList(new QuerySort("orderNo", SortDirection.DESC)))
            .build();

        CompiledQuery compiled = compiler.compile(plan(spec));

        Assertions.assertEquals(
            "select t.id as id,t.order_no as orderNo from t_order t where t.is_deleted = 0 and t.school_id = ? order by t.order_no DESC limit ?",
            compiled.getDataSql()
        );
        Assertions.assertEquals(Arrays.<Object>asList(10L, 20), compiled.getDataArgs());
        Assertions.assertEquals(
            "select count(1) from t_order t where t.is_deleted = 0 and t.school_id = ?",
            compiled.getCountSql()
        );
        Assertions.assertEquals(Collections.<Object>singletonList(10L), compiled.getCountArgs());
    }

    @Test
    void should_reject_relation_projection_filter_and_sort_before_sql_execution() {
        assertValidation("关联字段投影", QuerySpec.<OrderTestEntity>builder()
            .rootType(OrderTestEntity.class)
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(20)
            .selectFields(Collections.singletonList("items.skuCode"))
            .build());

        assertValidation("关联过滤", QuerySpec.<OrderTestEntity>builder()
            .rootType(OrderTestEntity.class)
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(20)
            .filters(Collections.singletonList(new QueryFilter("items.skuCode", FilterOperator.EQ, "SKU-1")))
            .build());

        assertValidation("关联排序", QuerySpec.<OrderTestEntity>builder()
            .rootType(OrderTestEntity.class)
            .resultType(OrderTestEntity.class)
            .op(QueryOperation.LIST)
            .limit(20)
            .sorts(Collections.singletonList(new QuerySort("items.skuCode", SortDirection.ASC)))
            .build());
    }

    private void assertValidation(String expectedMessagePart, QuerySpec<OrderTestEntity> spec) {
        ValidationException ex = Assertions.assertThrows(ValidationException.class, () -> compiler.compile(plan(spec)));
        Assertions.assertTrue(ex.getMessage().contains(expectedMessagePart));
    }

    private QueryPlan plan(QuerySpec<OrderTestEntity> spec) {
        return new QueryPlan(
            spec,
            orderMeta,
            RelationGraph.empty(),
            QueryStrategy.ROOT_FIRST,
            spec.getOp(),
            CrudDataScope.allowAll(),
            spec.getFilters()
        );
    }
}
