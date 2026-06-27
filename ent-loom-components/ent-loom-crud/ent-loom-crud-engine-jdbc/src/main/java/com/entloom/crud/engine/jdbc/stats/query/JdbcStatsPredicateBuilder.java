package com.entloom.crud.engine.jdbc.stats.query;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.engine.jdbc.sql.JdbcPredicateBuilder;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.HavingClause;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.MetricDescriptor;
import com.entloom.crud.engine.jdbc.stats.query.JdbcStatsSqlModel.WhereClause;
import com.entloom.crud.core.capability.stats.StatsHaving;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 负责将治理范围、过滤条件和 having 条件转换为 SQL 谓词。
 */
final class JdbcStatsPredicateBuilder {

    /**
     * 构建 where 子句（逻辑删除 + 治理范围 + 调用方过滤）。
     */
    WhereClause buildWhereClause(StatsSpec spec, EntityMeta rootMeta) {
        List<String> predicates = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        if (hasText(rootMeta.getLogicDeleteField())) {
            String logicDeleteCol = rootMeta.resolveColumn(rootMeta.getLogicDeleteField());
            predicates.add("t." + logicDeleteCol + " = 0");
        }
        predicates.addAll(buildGovernancePredicates(spec.getGovernanceScope(), rootMeta, args));
        predicates.addAll(buildCallerFilterPredicates(spec.getFilters(), rootMeta, args));
        return new WhereClause(String.join(" and ", predicates), args);
    }

    /**
     * 构建 having 子句。
     */
    HavingClause buildHavingClause(
        StatsQueryPayload payload,
        Map<String, MetricDescriptor> metricsByAlias
    ) {
        List<String> predicates = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        for (StatsHaving having : payload.getHaving()) {
            String metricAlias = having.getMetric().trim().toLowerCase(Locale.ROOT);
            MetricDescriptor metric = metricsByAlias.get(metricAlias);
            if (metric == null) {
                throw new ValidationException("having.metric 必须引用已声明的 metrics.alias: " + having.getMetric());
            }
            appendPredicate(predicates, args, metric.getExpr(), having.getOp(), having.getValue(), having.getMetric());
        }
        return new HavingClause(String.join(" and ", predicates), args);
    }

    private List<String> buildGovernancePredicates(CrudDataScope scope, EntityMeta rootMeta, List<Object> args) {
        List<String> predicates = new ArrayList<String>();
        if (scope == null || scope.isExplicitAll()) {
            return predicates;
        }
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            String column = rootMeta.resolveColumn(entry.getKey());
            if (column == null) {
                throw new DataScopeDeniedException("不支持的治理范围维度: " + entry.getKey());
            }
            JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, "t." + column, entry.getValue(), "governance scope");
        }
        return predicates;
    }

    private List<String> buildCallerFilterPredicates(List<QueryFilter> filters, EntityMeta rootMeta, List<Object> args) {
        List<String> predicates = new ArrayList<String>();
        for (QueryFilter filter : filters) {
            if (filter.getField().contains(".")) {
                throw new ValidationException("统计查询不支持关联过滤: " + filter.getField());
            }
            String column = rootMeta.resolveColumn(filter.getField());
            if (column == null) {
                throw new ValidationException("未知过滤字段: " + filter.getField());
            }
            appendPredicate(predicates, args, "t." + column, filter.getOperator(), filter.getValue(), filter.getField());
        }
        return predicates;
    }

    private void appendPredicate(
        List<String> predicates,
        List<Object> args,
        String qualified,
        FilterOperator op,
        Object value,
        String field
    ) {
        if (op == null) {
            throw new ValidationException("操作符不能为空: " + field);
        }
        switch (op) {
            case EQ:
                JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, qualified, value, field);
                break;
            case NE:
                predicates.add(qualified + " <> ?");
                args.add(value);
                break;
            case GT:
                predicates.add(qualified + " > ?");
                args.add(value);
                break;
            case GE:
                predicates.add(qualified + " >= ?");
                args.add(value);
                break;
            case LT:
                predicates.add(qualified + " < ?");
                args.add(value);
                break;
            case LE:
                predicates.add(qualified + " <= ?");
                args.add(value);
                break;
            case LIKE:
                predicates.add(qualified + " like ?");
                args.add(value);
                break;
            case IS_NULL:
                predicates.add(qualified + " is null");
                break;
            case IS_NOT_NULL:
                predicates.add(qualified + " is not null");
                break;
            case BETWEEN:
                if (!(value instanceof List<?>)) {
                    throw new ValidationException("BETWEEN 操作需要两个值: " + field);
                }
                List<?> betweenValues = (List<?>) value;
                if (betweenValues.size() != 2) {
                    throw new ValidationException("BETWEEN 操作需要两个值: " + field);
                }
                predicates.add(qualified + " between ? and ?");
                args.add(betweenValues.get(0));
                args.add(betweenValues.get(1));
                break;
            case IN:
            case NOT_IN:
                if (!(value instanceof Collection<?>)) {
                    throw new ValidationException(op + " 需要非空集合: " + field);
                }
                Collection<?> values = (Collection<?>) value;
                if (values.isEmpty()) {
                    throw new ValidationException(op + " 需要非空集合: " + field);
                }
                String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(","));
                predicates.add(qualified + (op == FilterOperator.IN ? " in (" : " not in (") + placeholders + ")");
                args.addAll(values);
                break;
            default:
                throw new ValidationException("不支持的操作符: " + op);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
