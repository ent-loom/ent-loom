package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsAggFunction;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsHaving;
import com.entloom.crud.core.capability.stats.StatsMetric;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 统计 DSL 语义校验器。
 */
public class StatsValidator {
    private static final int MAX_METRICS = 20;
    private static final int MAX_GROUPS = 10;
    private static final String ALIAS_REGEX = "^[A-Za-z_][A-Za-z0-9_]*$";

    /**
     * 校验统计查询 payload。
     */
    public void validate(StatsSpec spec, EntityMeta rootMeta, StatsQueryPayload payload) {
        if (spec == null || rootMeta == null || payload == null) {
            throw new ValidationException("统计查询参数不完整");
        }
        StatsQueryMode mode = spec.getMode();
        if (mode == StatsQueryMode.PAGE && payload.getGroupBy().isEmpty()) {
            throw new ValidationException("无 groupBy 的聚合查询不支持分页，请使用标量 stats");
        }

        if (payload.getMetrics().isEmpty()) {
            throw new ValidationException("统计查询至少需要一个 metrics 指标");
        }
        if (payload.getMetrics().size() > MAX_METRICS) {
            throw new ValidationException("metrics 数量超过上限 " + MAX_METRICS);
        }
        if (payload.getGroupBy().size() > MAX_GROUPS) {
            throw new ValidationException("groupBy 数量超过上限 " + MAX_GROUPS);
        }

        Set<String> metricAliases = new LinkedHashSet<String>();
        Set<String> dimensionAliases = new LinkedHashSet<String>();
        for (StatsGroupBy group : payload.getGroupBy()) {
            String field = requireText(group == null ? null : group.getField(), "groupBy.field");
            assertAllowedField(rootMeta, field);
            String alias = normalizeAlias(group.getAlias(), field);
            assertAlias(alias, "groupBy.alias");
            if (!dimensionAliases.add(alias)) {
                throw new ValidationException("groupBy.alias 重复: " + alias);
            }
        }

        for (StatsMetric metric : payload.getMetrics()) {
            String aggRaw = requireText(metric == null ? null : metric.getAgg(), "metrics.agg");
            StatsAggFunction agg = StatsAggFunction.from(aggRaw);
            if (agg == null) {
                throw new ValidationException("不支持的聚合函数: " + aggRaw);
            }
            String field = metric.getField();
            if (agg != StatsAggFunction.COUNT || hasText(field)) {
                field = requireText(field, "metrics.field");
                assertAllowedField(rootMeta, field);
            }
            String alias = normalizeAlias(metric.getAlias(), defaultMetricAlias(agg, field));
            assertAlias(alias, "metrics.alias");
            if (!metricAliases.add(alias)) {
                throw new ValidationException("metrics.alias 重复: " + alias);
            }
            if (dimensionAliases.contains(alias)) {
                throw new ValidationException("groupBy.alias 与 metrics.alias 重名: " + alias);
            }
        }

        for (QueryFilter filter : spec.getFilters()) {
            validateFilter(rootMeta, filter, "filters");
        }

        for (QuerySort sort : spec.getSorts()) {
            if (sort == null || !hasText(sort.getField())) {
                throw new ValidationException("sorts.field 不能为空");
            }
            if (sort.getDirection() == null) {
                throw new ValidationException("sorts.direction 不能为空");
            }
            if (sort.getTarget() == null) {
                throw new ValidationException("sorts.target 不能为空");
            }
            if (sort.getTarget() == SortTarget.AUTO) {
                throw new ValidationException("sorts.target 不能为 AUTO");
            }
        }

        for (StatsHaving having : payload.getHaving()) {
            if (having == null) {
                throw new ValidationException("having 条目不能为空");
            }
            String metric = requireText(having.getMetric(), "having.metric");
            if (!metricAliases.contains(metric)) {
                throw new ValidationException("having.metric 必须引用已声明的 metrics.alias: " + metric);
            }
            FilterOperator op = having.getOp();
            if (op == null) {
                throw new ValidationException("having.op 不能为空");
            }
            if (op != FilterOperator.IS_NULL && op != FilterOperator.IS_NOT_NULL && having.getValue() == null) {
                throw new ValidationException("having.value 不能为空");
            }
        }

        validateTime(rootMeta, spec.getTime());
    }

    private void validateFilter(EntityMeta rootMeta, QueryFilter filter, String path) {
        if (filter == null) {
            throw new ValidationException(path + " 条目不能为空");
        }
        assertAllowedField(rootMeta, requireText(filter.getField(), path + ".field"));
        if (filter.getOperator() == null) {
            throw new ValidationException(path + ".op 不能为空");
        }
    }

    private void validateTime(EntityMeta rootMeta, QueryTimeRange time) {
        if (time == null) {
            return;
        }
        if (!hasText(time.getStart()) && !hasText(time.getEnd())) {
            return;
        }
        assertAllowedField(rootMeta, requireText(time.getField(), "time.field"));
    }

    private void assertAllowedField(EntityMeta rootMeta, String field) {
        if (!rootMeta.getAllowedFields().contains(field)) {
            throw new ValidationException("统计字段不在白名单内: " + field);
        }
    }

    private String normalizeAlias(String alias, String fallback) {
        return hasText(alias) ? alias.trim() : fallback;
    }

    private void assertAlias(String alias, String field) {
        if (!hasText(alias)) {
            throw new ValidationException(field + " 不能为空");
        }
        if (!alias.matches(ALIAS_REGEX)) {
            throw new ValidationException(field + " 仅支持字母数字下划线，且不能数字开头: " + alias);
        }
    }

    private String defaultMetricAlias(StatsAggFunction agg, String field) {
        String suffix = hasText(field) ? field.trim() : "all";
        return agg.name().toLowerCase() + "_" + suffix;
    }

    private String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new ValidationException(field + " 不能为空");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
