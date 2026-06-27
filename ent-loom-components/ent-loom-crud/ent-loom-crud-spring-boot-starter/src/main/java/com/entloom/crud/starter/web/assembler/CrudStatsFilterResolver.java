package com.entloom.crud.starter.web.assembler;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.dto.CrudStatsHttpRequest;
import com.entloom.crud.starter.web.time.CrudTimeFilterResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 负责汇总并规范化 stats 过滤条件。
 */
final class CrudStatsFilterResolver {
    private final CrudTimeFilterResolver timeFilterResolver;
    private final CrudStringFilterPolicy stringFilterPolicy;

    CrudStatsFilterResolver(CrudTimeFilterResolver timeFilterResolver) {
        this(timeFilterResolver, CrudStringFilterPolicy.disabled());
    }

    CrudStatsFilterResolver(CrudTimeFilterResolver timeFilterResolver, CrudStringFilterPolicy stringFilterPolicy) {
        this.timeFilterResolver = timeFilterResolver;
        this.stringFilterPolicy = stringFilterPolicy == null ? CrudStringFilterPolicy.disabled() : stringFilterPolicy;
    }

    /**
     * 合并 options 中各种过滤写法，并追加时间过滤条件。
     */
    List<QueryFilter> resolveMergedFilters(CrudStatsHttpRequest request, Class<?> rootType) {
        List<QueryFilter> mergedFilters = new ArrayList<QueryFilter>();
        mergedFilters.addAll(request.getOptions().getFilters());
        mergedFilters.addAll(request.getOptions().getFilterList());
        mergedFilters.addAll(resolveFilterValueShorthand(request.getOptions().getFilter(), rootType));
        mergedFilters.addAll(resolveFilterShorthand(request.getOptions().getFilterMap()));
        mergedFilters.addAll(timeFilterResolver.resolveFilters(request.getOptions().getTime(), rootType));
        return mergedFilters;
    }

    private List<QueryFilter> resolveFilterShorthand(Map<String, QueryFilter> filterMap) {
        List<QueryFilter> filters = new ArrayList<QueryFilter>();
        if (filterMap == null || filterMap.isEmpty()) {
            return filters;
        }
        for (Map.Entry<String, QueryFilter> entry : filterMap.entrySet()) {
            QueryFilter filter = entry.getValue();
            if (filter == null) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "options.filterMap." + entry.getKey() + " 不能为空");
            }
            String resolvedField = filter.getField();
            if (resolvedField == null || resolvedField.trim().isEmpty()) {
                resolvedField = resolveFieldOrThrow(entry.getKey(), "options.filterMap");
            } else {
                resolvedField = resolvedField.trim();
            }
            if (isIgnorableBlankStringValue(filter.getOperator(), filter.getValue())) {
                continue;
            }
            if (filter.getOperator() == null) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "options.filterMap." + entry.getKey() + ".op 不能为空");
            }
            filters.add(new QueryFilter(resolvedField, filter.getOperator(), filter.getValue()));
        }
        return filters;
    }

    private List<QueryFilter> resolveFilterValueShorthand(Map<String, Object> filter, Class<?> rootType) {
        List<QueryFilter> filters = new ArrayList<QueryFilter>();
        if (filter == null || filter.isEmpty()) {
            return filters;
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String field = resolveFieldOrThrow(entry.getKey(), "options.filter");
            Object value = entry.getValue();
            if (value == null) {
                filters.add(new QueryFilter(field, FilterOperator.IS_NULL, null));
                continue;
            }
            if (isObjectFilterValue(value)) {
                throw new CrudException(
                    CrudErrorCode.VALIDATION_ERROR,
                    "options.filter." + field + " 不支持对象值，仅支持 null/标量/非空集合；对象过滤请使用 options.filters / options.filterMap / options.filterList"
                );
            }
            if (isBlankStringValue(value)) {
                continue;
            }
            if (value instanceof Collection<?>) {
                Collection<?> values = (Collection<?>) value;
                if (values.isEmpty()) {
                    throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "options.filter." + field + " 需要非空集合");
                }
                filters.add(new QueryFilter(field, FilterOperator.IN, value));
                continue;
            }
            filters.add(stringFilterPolicy.resolveScalarFilter(rootType, field, value));
        }
        return filters;
    }

    private String resolveFieldOrThrow(String rawField, String prefix) {
        if (rawField == null || rawField.trim().isEmpty()) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, prefix + ".field 不能为空");
        }
        return rawField.trim();
    }

    private boolean isIgnorableBlankStringValue(FilterOperator operator, Object value) {
        if (!isBlankStringValue(value)) {
            return false;
        }
        return operator != FilterOperator.IS_NULL && operator != FilterOperator.IS_NOT_NULL;
    }

    private boolean isBlankStringValue(Object value) {
        return value instanceof String && ((String) value).trim().isEmpty();
    }

    private boolean isObjectFilterValue(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return false;
        }
        return true;
    }
}
