package com.entloom.crud.engine.jdbc.stats.query;

import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.CompiledStatsSql;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.DimensionDescriptor;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.MetricDescriptor;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsResultColumns;
import com.entloom.crud.core.capability.stats.StatsResultPage;
import com.entloom.crud.core.capability.stats.StatsResultRow;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 负责将 JDBC 原始结果映射为统计返回模型。
 */
final class JdbcStatsResultMapper {

    StatsResultColumns buildColumns(CompiledStatsSql compiled) {
        StatsResultColumns columns = new StatsResultColumns();
        columns.setDimensions(compiled.getDimensions().stream().map(DimensionDescriptor::getAlias).collect(Collectors.toList()));
        columns.setMetrics(compiled.getMetrics().stream().map(MetricDescriptor::getAlias).collect(Collectors.toList()));
        return columns;
    }

    Map<String, Object> adaptScalar(List<Map<String, Object>> rawRows, CompiledStatsSql compiled) {
        if (rawRows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> source = rawRows.get(0);
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        for (MetricDescriptor metric : compiled.getMetrics()) {
            metrics.put(metric.getAlias(), normalizeMetricValue(readColumnValue(source, metric.getAlias())));
        }
        return metrics;
    }

    List<StatsResultRow> adaptRows(List<Map<String, Object>> rawRows, CompiledStatsSql compiled) {
        List<StatsResultRow> rows = new ArrayList<StatsResultRow>(rawRows.size());
        for (Map<String, Object> raw : rawRows) {
            Map<String, Object> dimensions = new LinkedHashMap<String, Object>();
            for (DimensionDescriptor dimension : compiled.getDimensions()) {
                dimensions.put(dimension.getAlias(), readColumnValue(raw, dimension.getAlias()));
            }
            Map<String, Object> metrics = new LinkedHashMap<String, Object>();
            for (MetricDescriptor metric : compiled.getMetrics()) {
                metrics.put(metric.getAlias(), normalizeMetricValue(readColumnValue(raw, metric.getAlias())));
            }
            StatsResultRow row = new StatsResultRow();
            row.setDimensions(dimensions);
            row.setMetrics(metrics);
            rows.add(row);
        }
        return rows;
    }

    Map<String, Object> adaptSummary(List<Map<String, Object>> rows, CompiledStatsSql compiled) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> source = rows.get(0);
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        for (MetricDescriptor metric : compiled.getMetrics()) {
            summary.put(metric.getAlias(), normalizeMetricValue(readColumnValue(source, metric.getAlias())));
        }
        return summary;
    }

    StatsResultPage buildPage(StatsSpec spec, StatsQueryPayload payload, int returned, Long totalGroups) {
        StatsResultPage page = new StatsResultPage();
        int limit = spec.getPage() == null ? spec.getLimit() : spec.getPage().getLimit();
        page.setPage(spec.getPage() == null ? 1 : spec.getPage().getPage());
        page.setLimit(limit);
        page.setReturned(returned);
        page.setNextCursor(null);
        if (Boolean.TRUE.equals(payload.getIncludeTotalGroups())) {
            page.setTotalGroups(totalGroups == null ? 0L : totalGroups);
        }
        return page;
    }

    private Object readColumnValue(Map<String, Object> row, String alias) {
        if (row.containsKey(alias)) {
            return row.get(alias);
        }
        String lower = alias.toLowerCase(Locale.ROOT);
        if (row.containsKey(lower)) {
            return row.get(lower);
        }
        String upper = alias.toUpperCase(Locale.ROOT);
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        return null;
    }

    private Object normalizeMetricValue(Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        return value;
    }
}
