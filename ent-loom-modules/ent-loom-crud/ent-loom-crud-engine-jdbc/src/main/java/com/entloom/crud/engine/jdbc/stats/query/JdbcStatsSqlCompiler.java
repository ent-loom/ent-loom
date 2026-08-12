package com.entloom.crud.engine.jdbc.stats.query;

import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.CompiledStatsSql;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.DimensionDescriptor;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.HavingClause;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.MetricDescriptor;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.WhereClause;
import com.entloom.crud.core.capability.stats.StatsAggFunction;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsMetric;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsTimeGranularity;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 负责将统计查询规格编译为可执行 SQL。
 */
final class JdbcStatsSqlCompiler {
    private final JdbcDialect dialect;
    private final JdbcStatsPredicateBuilder predicateBuilder;
    private static final int DEFAULT_LIST_LIMIT = SpecValidator.DEFAULT_LIST_LIMIT;

    JdbcStatsSqlCompiler(JdbcDialect dialect, JdbcStatsPredicateBuilder predicateBuilder) {
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.predicateBuilder = predicateBuilder == null ? new JdbcStatsPredicateBuilder() : predicateBuilder;
    }

    /**
     * 统一编译主查询、总组数查询和汇总查询。
     */
    CompiledStatsSql compile(StatsSpec spec, EntityMeta rootMeta, StatsQueryPayload payload) {
        List<DimensionDescriptor> dimensions = buildDimensions(payload, rootMeta);
        List<MetricDescriptor> metrics = buildMetrics(payload, rootMeta);
        Map<String, DimensionDescriptor> dimensionsByAlias = dimensions.stream().collect(Collectors.toMap(
            d -> d.getAlias().toLowerCase(Locale.ROOT),
            d -> d,
            (left, right) -> left,
            LinkedHashMap::new
        ));
        Map<String, DimensionDescriptor> dimensionsByField = dimensions.stream().collect(Collectors.toMap(
            d -> d.getField().toLowerCase(Locale.ROOT),
            d -> d,
            (left, right) -> left,
            LinkedHashMap::new
        ));
        Map<String, MetricDescriptor> metricsByAlias = metrics.stream().collect(Collectors.toMap(
            m -> m.getAlias().toLowerCase(Locale.ROOT),
            m -> m,
            (left, right) -> left,
            LinkedHashMap::new
        ));

        WhereClause where = predicateBuilder.buildWhereClause(spec, rootMeta);
        HavingClause having = predicateBuilder.buildHavingClause(payload, metricsByAlias);

        String baseFrom = " from " + rootMeta.getTable() + " t";
        String groupBySql = dimensions.isEmpty()
            ? ""
            : " group by " + dimensions.stream().map(DimensionDescriptor::getGroupExpr).collect(Collectors.joining(","));
        String havingSql = having.getSql().isEmpty() ? "" : " having " + having.getSql();

        String selectSql = buildSelectSql(dimensions, metrics);
        String baseSql = selectSql + baseFrom + buildWhereSql(where) + groupBySql + havingSql;

        List<Object> rowsArgs = new ArrayList<Object>(where.getArgs());
        rowsArgs.addAll(having.getArgs());
        StringBuilder rowsSql = new StringBuilder(baseSql);
        rowsSql.append(buildOrderBy(spec.getSorts(), dimensionsByAlias, dimensionsByField, metricsByAlias));
        appendLimit(spec, rowsSql, rowsArgs);

        String totalGroupsSql = "select count(1) from (select 1" + baseFrom + buildWhereSql(where) + groupBySql + havingSql + ") g";
        List<Object> totalGroupsArgs = new ArrayList<Object>(where.getArgs());
        totalGroupsArgs.addAll(having.getArgs());

        String summarySelect = "select " + metrics.stream()
            .map(metric -> metric.getExpr() + " as " + metric.getAlias())
            .collect(Collectors.joining(","));
        String summarySql = summarySelect + baseFrom + buildWhereSql(where);
        List<Object> summaryArgs = new ArrayList<Object>(where.getArgs());

        return new CompiledStatsSql(
            rowsSql.toString(),
            rowsArgs,
            totalGroupsSql,
            totalGroupsArgs,
            summarySql,
            summaryArgs,
            dimensions,
            metrics
        );
    }

    private String buildSelectSql(List<DimensionDescriptor> dimensions, List<MetricDescriptor> metrics) {
        List<String> selectItems = new ArrayList<String>();
        for (DimensionDescriptor dimension : dimensions) {
            selectItems.add(dimension.getSelectExpr() + " as " + dimension.getAlias());
        }
        for (MetricDescriptor metric : metrics) {
            selectItems.add(metric.getExpr() + " as " + metric.getAlias());
        }
        return "select " + String.join(",", selectItems);
    }

    private List<DimensionDescriptor> buildDimensions(StatsQueryPayload payload, EntityMeta rootMeta) {
        List<DimensionDescriptor> dimensions = new ArrayList<DimensionDescriptor>();
        Set<String> aliases = new LinkedHashSet<String>();
        for (StatsGroupBy groupBy : payload.getGroupBy()) {
            String field = groupBy.getField().trim();
            String alias = hasText(groupBy.getAlias()) ? groupBy.getAlias().trim() : field;
            if (!aliases.add(alias.toLowerCase(Locale.ROOT))) {
                throw new ValidationException("groupBy.alias 重复: " + alias);
            }
            String column = rootMeta.resolveColumn(field);
            if (column == null) {
                throw new ValidationException("未知 groupBy 字段: " + field);
            }
            StatsTimeGranularity granularity = StatsTimeGranularity.from(groupBy.getGranularity());
            String selectExpr = "t." + column;
            String groupExpr = selectExpr;
            if (granularity != null) {
                switch (granularity) {
                    case DAY:
                        selectExpr = "date(t." + column + ")";
                        groupExpr = "date(t." + column + ")";
                        break;
                    default:
                        break;
                }
            } else if (hasText(groupBy.getGranularity())) {
                throw new ValidationException("不支持的时间分桶: " + groupBy.getGranularity());
            }
            dimensions.add(new DimensionDescriptor(field, alias, selectExpr, groupExpr));
        }
        return dimensions;
    }

    private List<MetricDescriptor> buildMetrics(StatsQueryPayload payload, EntityMeta rootMeta) {
        List<MetricDescriptor> metrics = new ArrayList<MetricDescriptor>();
        Set<String> aliases = new LinkedHashSet<String>();
        for (StatsMetric metric : payload.getMetrics()) {
            StatsAggFunction agg = StatsAggFunction.from(metric.getAgg());
            String field = metric.getField();
            String alias = hasText(metric.getAlias()) ? metric.getAlias().trim() : defaultMetricAlias(agg, field);
            if (!aliases.add(alias.toLowerCase(Locale.ROOT))) {
                throw new ValidationException("metrics.alias 重复: " + alias);
            }
            String expr;
            if (agg == StatsAggFunction.COUNT) {
                if (!hasText(field) || "*".equals(field.trim())) {
                    expr = "count(1)";
                } else {
                    String countColumn = rootMeta.resolveColumn(field.trim());
                    if (countColumn == null) {
                        throw new ValidationException("未知指标字段: " + field);
                    }
                    expr = "count(t." + countColumn + ")";
                }
            } else {
                String column = rootMeta.resolveColumn(field == null ? null : field.trim());
                if (column == null) {
                    throw new ValidationException("未知指标字段: " + field);
                }
                expr = agg.name() + "(t." + column + ")";
            }
            metrics.add(new MetricDescriptor(alias, expr));
        }
        return metrics;
    }

    private String defaultMetricAlias(StatsAggFunction agg, String field) {
        String suffix = hasText(field) ? field.trim() : "all";
        return agg.name().toLowerCase(Locale.ROOT) + "_" + suffix;
    }

    private String buildOrderBy(
        List<QuerySort> sorts,
        Map<String, DimensionDescriptor> dimensionsByAlias,
        Map<String, DimensionDescriptor> dimensionsByField,
        Map<String, MetricDescriptor> metricsByAlias
    ) {
        if (sorts == null || sorts.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<String>();
        for (QuerySort sort : sorts) {
            if (sort == null || !hasText(sort.getField())) {
                throw new ValidationException("sorts.field 不能为空");
            }
            if (sort.getDirection() == null) {
                throw new ValidationException("sorts.direction 不能为空");
            }
            String normalized = sort.getField().trim().toLowerCase(Locale.ROOT);
            SortTarget target = sort.getTarget() == null ? SortTarget.AUTO : sort.getTarget();
            switch (target) {
                case METRIC:
                    appendMetricSortClause(clauses, metricsByAlias, normalized, sort);
                    break;
                case DIMENSION:
                    appendDimensionSortClause(clauses, dimensionsByAlias, dimensionsByField, normalized, sort);
                    break;
                case FIELD:
                    appendFieldSortClause(clauses, dimensionsByField, normalized, sort);
                    break;
                case AUTO:
                    if (appendMetricSortClauseSafely(clauses, metricsByAlias, normalized, sort)) {
                        break;
                    }
                    if (appendDimensionSortClauseSafely(clauses, dimensionsByAlias, dimensionsByField, normalized, sort)) {
                        break;
                    }
                    throw new ValidationException("统计排序字段未声明在 groupBy/metrics 中: " + sort.getField());
                default:
                    throw new ValidationException("不支持的排序目标: " + target);
            }
        }
        return clauses.isEmpty() ? "" : " order by " + String.join(",", clauses);
    }

    private void appendMetricSortClause(
        List<String> clauses,
        Map<String, MetricDescriptor> metricsByAlias,
        String normalizedField,
        QuerySort sort
    ) {
        MetricDescriptor metric = metricsByAlias.get(normalizedField);
        if (metric == null) {
            throw new ValidationException("统计指标排序字段未声明在 metrics 中: " + sort.getField());
        }
        clauses.add(metric.getExpr() + " " + sort.getDirection().name());
    }

    private boolean appendMetricSortClauseSafely(
        List<String> clauses,
        Map<String, MetricDescriptor> metricsByAlias,
        String normalizedField,
        QuerySort sort
    ) {
        MetricDescriptor metric = metricsByAlias.get(normalizedField);
        if (metric == null) {
            return false;
        }
        clauses.add(metric.getExpr() + " " + sort.getDirection().name());
        return true;
    }

    private void appendDimensionSortClause(
        List<String> clauses,
        Map<String, DimensionDescriptor> dimensionsByAlias,
        Map<String, DimensionDescriptor> dimensionsByField,
        String normalizedField,
        QuerySort sort
    ) {
        if (!appendDimensionSortClauseSafely(clauses, dimensionsByAlias, dimensionsByField, normalizedField, sort)) {
            throw new ValidationException("统计维度排序字段未声明在 groupBy 中: " + sort.getField());
        }
    }

    private boolean appendDimensionSortClauseSafely(
        List<String> clauses,
        Map<String, DimensionDescriptor> dimensionsByAlias,
        Map<String, DimensionDescriptor> dimensionsByField,
        String normalizedField,
        QuerySort sort
    ) {
        DimensionDescriptor dimension = dimensionsByAlias.get(normalizedField);
        if (dimension == null) {
            dimension = dimensionsByField.get(normalizedField);
        }
        if (dimension == null) {
            return false;
        }
        clauses.add(dimension.getGroupExpr() + " " + sort.getDirection().name());
        return true;
    }

    private void appendFieldSortClause(
        List<String> clauses,
        Map<String, DimensionDescriptor> dimensionsByField,
        String normalizedField,
        QuerySort sort
    ) {
        DimensionDescriptor dimension = dimensionsByField.get(normalizedField);
        if (dimension == null) {
            throw new ValidationException("统计字段排序仅支持 groupBy.field: " + sort.getField());
        }
        clauses.add(dimension.getGroupExpr() + " " + sort.getDirection().name());
    }

    private void appendLimit(StatsSpec spec, StringBuilder sql, List<Object> args) {
        StatsQueryMode mode = spec.getMode();
        if (mode == StatsQueryMode.PAGE) {
            int limit = spec.getPage().getLimit();
            int offset = (spec.getPage().getPage() - 1) * limit;
            dialect.appendPageClause(sql, limit, offset, args);
            return;
        }
        if (mode == StatsQueryMode.LIST) {
            Integer limit = spec.getLimit();
            int effectiveLimit = limit == null || limit.intValue() <= 0 ? DEFAULT_LIST_LIMIT : limit.intValue();
            dialect.appendListClause(sql, effectiveLimit, args);
        }
    }

    private String buildWhereSql(WhereClause where) {
        return where.getSql().isEmpty() ? "" : " where " + where.getSql();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
