package com.entloom.crud.starter.web.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.CrudReadResultMode;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.starter.web.dto.CrudReadHttpRequest;
import com.entloom.crud.starter.web.dto.CrudReadOptions;
import com.entloom.crud.starter.web.dto.CrudTimeFilter;
import com.entloom.crud.starter.web.support.CrudRequestSupport;
import com.entloom.crud.starter.web.time.CrudTimeFilterResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 查询 HTTP DTO 到 QuerySpec 的组装器。
 */
public class CrudQuerySpecAssembler {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 100;
    /** 请求支持组件。 */
    private final CrudRequestSupport requestSupport;
    /** 时间过滤解析器。 */
    private final CrudTimeFilterResolver timeFilterResolver;
    /** 对象映射器。 */
    private final ObjectMapper objectMapper;
    /** 默认读结果模式（MAP/ENTITY）。 */
    private final CrudReadResultMode defaultResultMode;
    /** 字符串简写过滤策略。 */
    private final CrudStringFilterPolicy stringFilterPolicy;
    private final EntityMetaRegistry entityMetaRegistry;

    public CrudQuerySpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper
    ) {
        this(requestSupport, timeFilterResolver, objectMapper, CrudReadResultMode.MAP,
            CrudStringFilterPolicy.disabled(), null);
    }

    public CrudQuerySpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper,
        CrudReadResultMode defaultResultMode
    ) {
        this(requestSupport, timeFilterResolver, objectMapper, defaultResultMode,
            CrudStringFilterPolicy.disabled(), null);
    }

    public CrudQuerySpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper,
        CrudReadResultMode defaultResultMode,
        CrudStringFilterPolicy stringFilterPolicy
    ) {
        this(requestSupport, timeFilterResolver, objectMapper, defaultResultMode, stringFilterPolicy, null);
    }

    public CrudQuerySpecAssembler(
        CrudRequestSupport requestSupport,
        CrudTimeFilterResolver timeFilterResolver,
        ObjectMapper objectMapper,
        CrudReadResultMode defaultResultMode,
        CrudStringFilterPolicy stringFilterPolicy,
        EntityMetaRegistry entityMetaRegistry
    ) {
        this.requestSupport = requestSupport;
        this.timeFilterResolver = timeFilterResolver;
        this.objectMapper = objectMapper;
        this.defaultResultMode = defaultResultMode == null ? CrudReadResultMode.MAP : defaultResultMode;
        this.stringFilterPolicy = stringFilterPolicy == null ? CrudStringFilterPolicy.disabled() : stringFilterPolicy;
        this.entityMetaRegistry = entityMetaRegistry;
    }

    public QuerySpec<Object> assembleRead(
        QueryOperation routeOp,
        String routeEntity,
        String scene,
        CrudReadHttpRequest request,
        SubjectContext subject
    ) {
        CrudReadHttpRequest actualRequest = request == null ? new CrudReadHttpRequest() : request;
        RequestContractValidator.validateRead(actualRequest);
        rejectFindOnePaginationOptions(routeOp, actualRequest);
        validateDetailTarget(routeOp, actualRequest);
        validateReadOptionValues(actualRequest);
        return assemble(
            routeEntity,
            routeOp,
            scene,
            actualRequest,
            subject,
            resolvePreferredResultType(actualRequest)
        );
    }

    private void validateReadOptionValues(CrudReadHttpRequest request) {
        if (request == null || request.getOptions() == null) {
            return;
        }
        request.getOptions().resolveResultMode();
        request.getOptions().resolveCountMode();
    }

    private void rejectFindOnePaginationOptions(QueryOperation routeOp, CrudReadHttpRequest request) {
        if (routeOp != QueryOperation.FIND_ONE || request == null || request.getOptions() == null) {
            return;
        }
        if (request.getOptions().getPage() != null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "findOne 不支持 options.page");
        }
        if (request.getOptions().getLimit() != null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "findOne 不支持 options.limit");
        }
    }

    private void validateDetailTarget(QueryOperation routeOp, CrudReadHttpRequest request) {
        if (routeOp != QueryOperation.DETAIL) {
            if (request != null && request.getId() != null) {
                throw httpValidation("只有 detail 支持顶层 id", "DETAIL_TARGET_UNEXPECTED");
            }
            return;
        }
        Object id = request == null ? null : request.getId();
        if (!(id instanceof String || id instanceof Number) || id.toString().trim().isEmpty()) {
            throw httpValidation("detail 必须携带单个顶层 id", "DETAIL_TARGET_REQUIRED");
        }
        CrudReadOptions options = request.getOptions();
        if (options != null && (!options.getFilters().isEmpty() || !options.getFilterList().isEmpty()
            || !options.getFilter().isEmpty() || !options.getFilterMap().isEmpty() || options.getTime() != null)) {
            throw httpValidation("detail 只能携带顶层 id，不能同时携带筛选条件", "DETAIL_TARGET_MIXED");
        }
    }

    private CrudException httpValidation(String message, String reason) {
        return new CrudException(CrudErrorCode.VALIDATION_ERROR, message)
            .withStage(CrudErrorStage.HTTP_CONTRACT)
            .withReason(reason);
    }

    QuerySpec<Object> assemble(
        String routeEntity,
        QueryOperation routeOp,
        String scene,
        CrudReadHttpRequest request,
        SubjectContext subject,
        Class<?> preferredResultType
    ) {
        request.setEntityCodes(requestSupport.normalizeEntityCodes(routeEntity, request.getEntityCodes()));
        List<Class<?>> entityClasses = requestSupport.resolveEntityClasses(request.getEntityCodes(), routeEntity);

        List<QueryFilter> filters = resolveFilters(request, entityClasses.get(0));
        if (routeOp == QueryOperation.DETAIL && request.getId() != null) {
            filters = new ArrayList<QueryFilter>(Collections.singletonList(
                new QueryFilter(resolveIdField(entityClasses.get(0)), FilterOperator.EQ, request.getId())));
        }

        QuerySpec.Builder<Object> builder = QuerySpec.<Object>builder()
            .scene(requestSupport.normalizeScene(scene))
            .rootType(entityClasses.get(0))
            .entityClasses(entityClasses)
            .subject(requestSupport.resolveSubject(subject))
            .expandRelations(request.getOptions().getExpandRelations())
            .op(routeOp)
            .resultType(resolveResultType(entityClasses, preferredResultType))
            .attributes(Collections.<String, Object>singletonMap("standardCrudHttp", Boolean.TRUE))
            .filters(filters)
            .sorts(resolveSorts(request))
            .selectFields(request.getOptions().getSelectFields())
            .time(toQueryTimeRange(request.getOptions().getTime()));

        if (routeOp == QueryOperation.PAGE) {
            Integer page = request.getOptions().getPage();
            Integer limit = request.getOptions().getLimit();
            int actualPage = page == null ? DEFAULT_PAGE : page.intValue();
            int actualLimit = limit == null ? DEFAULT_PAGE_LIMIT : limit.intValue();
            if (actualPage < 1 || actualLimit < 1 || actualLimit > MAX_PAGE_LIMIT) {
                throw httpValidation("分页参数必须满足 page >= 1 且 1 <= limit <= 100", "PAGE_LIMIT_OUT_OF_RANGE");
            }
            builder.page(new PageRequest(actualPage, actualLimit));
            builder.countMode(request.getOptions().resolveCountMode());
        } else if (routeOp == QueryOperation.LIST) {
            builder.limit(request.getOptions().getLimit());
        }
        return builder.build();
    }

    private String resolveIdField(Class<?> entityType) {
        EntityMeta meta = entityMetaRegistry == null ? null : entityMetaRegistry.getEntityMeta(entityType);
        if (meta == null || meta.getIdField() == null || meta.getIdField().trim().isEmpty()) {
            return "id";
        }
        return meta.getIdField();
    }

    @SuppressWarnings("unchecked")
    private Class<Object> resolveResultType(List<Class<?>> entityClasses, Class<?> preferredResultType) {
        Class<?> selected = preferredResultType == null ? entityClasses.get(0) : preferredResultType;
        return (Class<Object>) (Class<?>) selected;
    }

    private Class<?> resolvePreferredResultType(CrudReadHttpRequest request) {
        if (request == null || request.getOptions() == null) {
            return resolveTypeByMode(defaultResultMode, "ent.loom.crud.controller.default-read-result-mode");
        }
        String viewType = request.getOptions().getViewType();
        if (viewType != null && !viewType.trim().isEmpty()) {
            return requestSupport.resolveViewTypeOrThrow(viewType);
        }
        CrudReadResultMode resultMode = request.getOptions().resolveResultMode();
        if (resultMode == null) {
            return resolveTypeByMode(defaultResultMode, "ent.loom.crud.controller.default-read-result-mode");
        }
        return resolveTypeByMode(resultMode, "options.resultMode");
    }

    private Class<?> resolveTypeByMode(CrudReadResultMode mode, String sourceLabel) {
        CrudReadResultMode normalized = mode == null ? CrudReadResultMode.MAP : mode;
        if (normalized == CrudReadResultMode.ENTITY) {
            return null;
        }
        if (normalized == CrudReadResultMode.MAP) {
            return CrudRecord.class;
        }
        throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "不支持 " + sourceLabel + ": " + mode + "，仅支持 ENTITY 或 MAP");
    }

    private List<QueryFilter> resolveFilters(CrudReadHttpRequest request, Class<?> rootType) {
        List<QueryFilter> resolved = new ArrayList<QueryFilter>(request.getOptions().getFilters());
        resolved.addAll(request.getOptions().getFilterList());
        resolved.addAll(resolveFilterValueShorthand(request.getOptions().getFilter(), rootType));
        resolved.addAll(resolveFilterShorthand(request.getOptions().getFilterMap()));
        resolved.addAll(timeFilterResolver.resolveFilters(request.getOptions().getTime(), rootType));
        return resolved;
    }

    /**
     * 解析最简过滤参数：{"field": value}
     */
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

    /**
     * 解析简写过滤参数。
     */
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

    private String resolveFieldOrThrow(String rawField, String prefix) {
        if (rawField == null || rawField.trim().isEmpty()) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, prefix + ".field 不能为空");
        }
        return rawField.trim();
    }

    /**
     * 解析查询排序条件。
     */
    private List<QuerySort> resolveSorts(CrudReadHttpRequest request) {
        if (request.getOptions().getSorts() != null && !request.getOptions().getSorts().isEmpty()) {
            return request.getOptions().getSorts();
        }
        if (request.getOptions().getSort() != null) {
            return Collections.singletonList(request.getOptions().getSort());
        }
        return Collections.emptyList();
    }

    private QueryTimeRange toQueryTimeRange(CrudTimeFilter timeFilter) {
        if (timeFilter == null) {
            return null;
        }
        QueryTimeRange range = new QueryTimeRange();
        range.setField(timeFilter.getField());
        range.setStart(timeFilter.getStart());
        range.setEnd(timeFilter.getEnd());
        range.setTimezone(timeFilter.getTimezone());
        return range;
    }
}
