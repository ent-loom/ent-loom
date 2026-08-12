package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.capability.stats.StatsAggFunction;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsMetric;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责 stats 排序参数解析与 target 推断。
 */
final class CrudStatsSortResolver {

    /**
     * 解析排序来源并完成 target 自动推断。
     */
    List<QuerySort> resolveSorts(CrudStatsHttpRequest request, StatsQueryPayload payload) {
        List<QuerySort> sourceSorts;
        if (request.getOptions().getSorts() != null && !request.getOptions().getSorts().isEmpty()) {
            sourceSorts = request.getOptions().getSorts();
        } else if (request.getOptions().getSort() != null) {
            sourceSorts = Collections.singletonList(request.getOptions().getSort());
        } else {
            sourceSorts = Collections.emptyList();
        }

        if (sourceSorts.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> metricAliases = metricAliases(payload);
        Set<String> dimensionAliases = dimensionAliases(payload);
        Set<String> dimensionFields = dimensionFields(payload);
        List<QuerySort> normalized = new ArrayList<QuerySort>(sourceSorts.size());
        for (QuerySort sort : sourceSorts) {
            if (sort == null) {
                normalized.add(null);
                continue;
            }
            SortTarget target = sort.getTarget() == null ? SortTarget.AUTO : sort.getTarget();
            if (target == SortTarget.AUTO) {
                target = resolveAutoSortTarget(sort.getField(), metricAliases, dimensionAliases, dimensionFields);
            }
            normalized.add(new QuerySort(sort.getField(), sort.getDirection(), target));
        }
        return normalized;
    }

    private SortTarget resolveAutoSortTarget(
        String field,
        Set<String> metricAliases,
        Set<String> dimensionAliases,
        Set<String> dimensionFields
    ) {
        String normalized = field == null ? "" : field.trim().toLowerCase();
        boolean metric = metricAliases.contains(normalized);
        boolean dimension = dimensionAliases.contains(normalized) || dimensionFields.contains(normalized);
        if (metric && dimension) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "sorts.target 冲突，请显式指定 target: " + field);
        }
        if (metric) {
            return SortTarget.METRIC;
        }
        if (dimension) {
            return SortTarget.DIMENSION;
        }
        return SortTarget.FIELD;
    }

    private Set<String> metricAliases(StatsQueryPayload payload) {
        Set<String> aliases = new LinkedHashSet<String>();
        for (StatsMetric metric : payload.getMetrics()) {
            if (metric == null) {
                continue;
            }
            String alias = hasText(metric.getAlias()) ? metric.getAlias().trim() : defaultMetricAlias(metric);
            if (hasText(alias)) {
                aliases.add(alias.toLowerCase());
            }
        }
        return aliases;
    }

    private Set<String> dimensionAliases(StatsQueryPayload payload) {
        Set<String> aliases = new LinkedHashSet<String>();
        for (StatsGroupBy groupBy : payload.getGroupBy()) {
            if (groupBy == null) {
                continue;
            }
            String alias = hasText(groupBy.getAlias()) ? groupBy.getAlias().trim() : groupBy.getField();
            if (hasText(alias)) {
                aliases.add(alias.toLowerCase());
            }
        }
        return aliases;
    }

    private Set<String> dimensionFields(StatsQueryPayload payload) {
        Set<String> fields = new LinkedHashSet<String>();
        for (StatsGroupBy groupBy : payload.getGroupBy()) {
            if (groupBy == null || !hasText(groupBy.getField())) {
                continue;
            }
            fields.add(groupBy.getField().trim().toLowerCase());
        }
        return fields;
    }

    private String defaultMetricAlias(StatsMetric metric) {
        StatsAggFunction agg = StatsAggFunction.from(metric.getAgg());
        if (agg == null) {
            return null;
        }
        String suffix = hasText(metric.getField()) ? metric.getField().trim() : "all";
        return agg.name().toLowerCase() + "_" + suffix;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
