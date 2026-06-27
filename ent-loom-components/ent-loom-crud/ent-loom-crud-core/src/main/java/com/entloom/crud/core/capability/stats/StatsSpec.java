package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.api.model.SubjectContext;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.FilterableSpec;
import com.entloom.crud.core.runtime.spec.GovernableSpec;
import com.entloom.crud.core.runtime.spec.OperationKeySpec;
import com.entloom.crud.core.capability.stats.StatsGroupBy;
import com.entloom.crud.core.capability.stats.StatsHaving;
import com.entloom.crud.core.capability.stats.StatsMetric;
import com.entloom.crud.core.capability.stats.StatsQueryMode;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import com.entloom.crud.core.capability.stats.StatsResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * stats 专用协议对象（不可变）。
 */
public final class StatsSpec extends BaseSpec implements FilterableSpec, GovernableSpec<StatsSpec>, OperationKeySpec {
    /** 过滤条件列表。 */
    private final List<QueryFilter> filters;
    /** 排序条件列表。 */
    private final List<QuerySort> sorts;
    /** 时间范围。 */
    private final QueryTimeRange time;
    /** 分页请求。 */
    private final PageRequest page;
    /** 列表上限。 */
    private final Integer limit;
    /** 展开关联列表。 */
    private final List<String> expandRelations;
    /** 结果类型。 */
    private final Class<StatsResult> resultType;
    /** 统计查询载荷。 */
    private final StatsQueryPayload payload;
    /** 统计查询模式。 */
    private final StatsQueryMode mode;
    /** 是否附带执行元信息。 */
    private final boolean includeExecutionMeta;
    /** Stats 操作。 */
    private final StatsOperation operation;

    private StatsSpec(Builder builder) {
        super(builder);
        this.filters = Collections.unmodifiableList(copyFilters(builder.filters));
        this.sorts = Collections.unmodifiableList(copySorts(builder.sorts));
        this.time = copyTime(builder.time);
        this.page = copyPage(builder.page);
        this.limit = builder.limit;
        this.expandRelations = Collections.unmodifiableList(copyStrings(builder.expandRelations));
        this.resultType = builder.resultType == null ? StatsResult.class : builder.resultType;
        this.payload = copyPayload(builder.payload);
        this.mode = builder.mode == null ? StatsQueryMode.SCALAR : builder.mode;
        this.includeExecutionMeta = builder.includeExecutionMeta;
        this.operation = builder.operation == null ? StatsOperation.QUERY : builder.operation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public StatsOperation getOperation() {
        return operation;
    }

    public CrudOperationKey getOperationKey() {
        return CrudOperationKey.of(getOperation());
    }

    @Override
    public List<QueryFilter> getFilters() {
        return copyFilters(filters);
    }

    @Override
    public List<QuerySort> getSorts() {
        return copySorts(sorts);
    }

    @Override
    public QueryTimeRange getTime() {
        return copyTime(time);
    }

    public PageRequest getPage() {
        return copyPage(page);
    }

    public Integer getLimit() {
        return limit;
    }

    public List<String> getExpandRelations() {
        return copyStrings(expandRelations);
    }

    public Class<StatsResult> getResultType() {
        return resultType;
    }

    public StatsQueryPayload getPayload() {
        return copyPayload(payload);
    }

    public StatsQueryMode getMode() {
        return mode;
    }

    public boolean isIncludeExecutionMeta() {
        return includeExecutionMeta;
    }

    @Override
    public StatsSpec withSubject(SubjectContext subject) {
        return toBuilder().subject(subject).build();
    }

    @Override
    public StatsSpec withAttributes(Map<String, Object> attributes) {
        return toBuilder().attributes(attributes).build();
    }

    @Override
    public StatsSpec withGovernance(
        AccessDecision accessDecision,
        CrudDataScope grantedScope,
        CrudDataScope governanceScope
    ) {
        return toBuilder()
            .accessDecision(accessDecision)
            .grantedScope(grantedScope)
            .governanceScope(governanceScope)
            .build();
    }

    private Builder copyStatsTo(Builder builder) {
        copyBaseTo(builder);
        builder.filters(getFilters());
        builder.sorts(getSorts());
        builder.time(getTime());
        builder.page(getPage());
        builder.limit(limit);
        builder.expandRelations(getExpandRelations());
        builder.resultType(resultType);
        builder.payload(payload);
        builder.mode(mode);
        builder.includeExecutionMeta(includeExecutionMeta);
        builder.operation(operation);
        return builder;
    }

    public static final class Builder extends BaseSpec.AbstractBuilder<Builder> {
        private List<QueryFilter> filters = new ArrayList<QueryFilter>();
        private List<QuerySort> sorts = new ArrayList<QuerySort>();
        private QueryTimeRange time;
        private PageRequest page;
        private Integer limit;
        private List<String> expandRelations = new ArrayList<String>();
        private Class<StatsResult> resultType = StatsResult.class;
        private StatsQueryPayload payload;
        private StatsQueryMode mode = StatsQueryMode.SCALAR;
        private boolean includeExecutionMeta;
        private StatsOperation operation = StatsOperation.QUERY;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder operation(StatsOperation operation) {
            this.operation = operation == null ? StatsOperation.QUERY : operation;
            return this;
        }

        public Builder filters(List<QueryFilter> filters) {
            this.filters = copyFilters(filters);
            return this;
        }

        public Builder sorts(List<QuerySort> sorts) {
            this.sorts = copySorts(sorts);
            return this;
        }

        public Builder time(QueryTimeRange time) {
            this.time = copyTime(time);
            return this;
        }

        public Builder page(PageRequest page) {
            this.page = copyPage(page);
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder expandRelations(List<String> expandRelations) {
            this.expandRelations = copyStrings(expandRelations);
            return this;
        }

        public Builder resultType(Class<StatsResult> resultType) {
            this.resultType = resultType == null ? StatsResult.class : resultType;
            return this;
        }

        public Builder payload(StatsQueryPayload payload) {
            this.payload = copyPayload(payload);
            return this;
        }

        public Builder mode(StatsQueryMode mode) {
            this.mode = mode == null ? StatsQueryMode.SCALAR : mode;
            return this;
        }

        public Builder includeExecutionMeta(boolean includeExecutionMeta) {
            this.includeExecutionMeta = includeExecutionMeta;
            return this;
        }

        public Builder from(StatsSpec source) {
            if (source != null) {
                source.copyStatsTo(this);
            }
            return this;
        }

        public StatsSpec build() {
            return new StatsSpec(this);
        }
    }

    private static List<String> copyStrings(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }

    private static List<QueryFilter> copyFilters(List<QueryFilter> source) {
        List<QueryFilter> target = new ArrayList<QueryFilter>();
        if (source == null) {
            return target;
        }
        for (QueryFilter filter : source) {
            target.add(copyFilter(filter));
        }
        return target;
    }

    private static List<QuerySort> copySorts(List<QuerySort> source) {
        List<QuerySort> target = new ArrayList<QuerySort>();
        if (source == null) {
            return target;
        }
        for (QuerySort sort : source) {
            target.add(copySort(sort));
        }
        return target;
    }

    private static QueryFilter copyFilter(QueryFilter source) {
        if (source == null) {
            return null;
        }
        return new QueryFilter(source.getField(), source.getOperator(), source.getValue());
    }

    private static QuerySort copySort(QuerySort source) {
        if (source == null) {
            return null;
        }
        return new QuerySort(source.getField(), source.getDirection(), source.getTarget());
    }

    private static QueryTimeRange copyTime(QueryTimeRange source) {
        if (source == null) {
            return null;
        }
        QueryTimeRange target = new QueryTimeRange();
        target.setField(source.getField());
        target.setStart(source.getStart());
        target.setEnd(source.getEnd());
        target.setTimezone(source.getTimezone());
        return target;
    }

    private static PageRequest copyPage(PageRequest source) {
        if (source == null) {
            return null;
        }
        return new PageRequest(source.getPage(), source.getLimit());
    }

    private static StatsQueryPayload copyPayload(StatsQueryPayload source) {
        if (source == null) {
            return null;
        }
        StatsQueryPayload target = new StatsQueryPayload();
        target.setGroupBy(source.getGroupBy() == null ? null : new ArrayList<StatsGroupBy>(source.getGroupBy()));
        target.setMetrics(source.getMetrics() == null ? null : new ArrayList<StatsMetric>(source.getMetrics()));
        target.setHaving(source.getHaving() == null ? null : new ArrayList<StatsHaving>(source.getHaving()));
        target.setIncludeSummary(source.getIncludeSummary());
        target.setIncludeTotalGroups(source.getIncludeTotalGroups());
        return target;
    }
}
