package com.entloom.crud.engine.jdbc.stats.query;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.CompiledStatsSql;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsHaving;
import com.entloom.crud.core.capability.stats.StatsMetric;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JdbcStatsSqlCompilerTest {

    @Test
    void should_compile_page_stats_sql_with_filters_having_sort_and_summary() {
        StatsQueryPayload payload = new StatsQueryPayload();
        StatsGroupBy groupBy = new StatsGroupBy("paymentChannel");
        groupBy.setAlias("paymentChannel");
        payload.setGroupBy(Collections.singletonList(groupBy));
        payload.setMetrics(Collections.singletonList(new StatsMetric("SUM", "totalAmount", "totalAmountSum")));
        StatsHaving having = new StatsHaving();
        having.setMetric("totalAmountSum");
        having.setOp(FilterOperator.GT);
        having.setValue(new BigDecimal("100"));
        payload.setHaving(Collections.singletonList(having));

        StatsSpec spec = StatsSpec.builder()
            .rootType(TestOrder.class)
            .payload(payload)
            .mode(StatsQueryMode.PAGE)
            .page(new PageRequest(2, 10))
            .filters(Collections.singletonList(new QueryFilter("paid", FilterOperator.EQ, Boolean.TRUE)))
            .sorts(Collections.singletonList(new QuerySort("totalAmountSum", SortDirection.DESC, SortTarget.METRIC)))
            .governanceScope(CrudDataScope.allowAll())
            .build();

        CompiledStatsSql sql = new JdbcStatsSqlCompiler(StandardJdbcDialect.H2, new JdbcStatsPredicateBuilder())
            .compile(spec, entityMeta(), payload);

        Assertions.assertEquals(
            "select t.payment_channel as paymentChannel,SUM(t.total_amount) as totalAmountSum" +
                " from test_order t where t.deleted = 0 and t.paid = ? group by t.payment_channel" +
                " having SUM(t.total_amount) > ? order by SUM(t.total_amount) DESC limit ? offset ?",
            sql.getRowsSql()
        );
        Assertions.assertEquals(Arrays.asList(Boolean.TRUE, new BigDecimal("100"), 10, 10), sql.getRowsArgs());
        Assertions.assertEquals(
            "select count(1) from (select 1 from test_order t where t.deleted = 0 and t.paid = ?" +
                " group by t.payment_channel having SUM(t.total_amount) > ?) g",
            sql.getTotalGroupsSql()
        );
        Assertions.assertEquals(Arrays.asList(Boolean.TRUE, new BigDecimal("100")), sql.getTotalGroupsArgs());
        Assertions.assertEquals(
            "select SUM(t.total_amount) as totalAmountSum from test_order t where t.deleted = 0 and t.paid = ?",
            sql.getSummarySql()
        );
        Assertions.assertEquals(Collections.singletonList(Boolean.TRUE), sql.getSummaryArgs());
    }

    @Test
    void should_compile_day_bucket_dimension() {
        StatsQueryPayload payload = new StatsQueryPayload();
        StatsGroupBy groupBy = new StatsGroupBy("createdAt");
        groupBy.setAlias("createdDay");
        groupBy.setGranularity("DAY");
        payload.setGroupBy(Collections.singletonList(groupBy));
        payload.setMetrics(Collections.singletonList(new StatsMetric("COUNT", "id", "orderCount")));

        StatsSpec spec = StatsSpec.builder()
            .rootType(TestOrder.class)
            .payload(payload)
            .mode(StatsQueryMode.LIST)
            .limit(5)
            .governanceScope(CrudDataScope.allowAll())
            .build();

        CompiledStatsSql sql = new JdbcStatsSqlCompiler(StandardJdbcDialect.H2, new JdbcStatsPredicateBuilder())
            .compile(spec, entityMeta(), payload);

        Assertions.assertEquals(
            "select date(t.created_at) as createdDay,count(t.id) as orderCount" +
                " from test_order t where t.deleted = 0 group by date(t.created_at) limit ?",
            sql.getRowsSql()
        );
        Assertions.assertEquals(Collections.singletonList(5), sql.getRowsArgs());
    }

    @Test
    void should_reject_unsupported_time_bucket() {
        StatsQueryPayload payload = new StatsQueryPayload();
        StatsGroupBy groupBy = new StatsGroupBy("createdAt");
        groupBy.setGranularity("MONTH");
        payload.setGroupBy(Collections.singletonList(groupBy));
        payload.setMetrics(Collections.singletonList(new StatsMetric("COUNT", "id", "orderCount")));

        StatsSpec spec = StatsSpec.builder()
            .rootType(TestOrder.class)
            .payload(payload)
            .governanceScope(CrudDataScope.allowAll())
            .build();

        ValidationException ex = Assertions.assertThrows(
            ValidationException.class,
            () -> new JdbcStatsSqlCompiler(StandardJdbcDialect.H2, new JdbcStatsPredicateBuilder())
                .compile(spec, entityMeta(), payload)
        );
        Assertions.assertTrue(ex.getMessage().contains("MONTH"));
    }

    private static EntityMeta entityMeta() {
        Map<String, EntityFieldMeta> fields = new LinkedHashMap<String, EntityFieldMeta>();
        fields.put("id", field("id", Long.class, "id"));
        fields.put("paymentChannel", field("paymentChannel", String.class, "payment_channel"));
        fields.put("totalAmount", field("totalAmount", BigDecimal.class, "total_amount"));
        fields.put("paid", field("paid", Boolean.class, "paid"));
        fields.put("createdAt", field("createdAt", java.util.Date.class, "created_at"));
        fields.put("deleted", field("deleted", Integer.class, "deleted"));
        return new EntityMeta(
            TestOrder.class,
            new ResourceDescriptor(TestOrder.class, "TestOrder", "order-service", Collections.<String>emptyList()),
            "test_order",
            "id",
            "deleted",
            fields
        );
    }

    private static EntityFieldMeta field(String fieldName, Class<?> javaType, String columnName) {
        return new EntityFieldMeta(fieldName, javaType, columnName, true, false, true, true);
    }

    private static final class TestOrder {
    }
}
