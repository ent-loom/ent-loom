package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.ExportOperation;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出能力协议对象。
 */
public final class ExportSpec extends BaseSpec implements FilterableSpec, GovernableSpec<ExportSpec>, OperationKeySpec {
    private final ExportOperation operation;
    private final String format;
    private final String fileName;
    private final String taskId;
    private final boolean async;
    private final List<String> fields;
    private final List<QueryFilter> filters;
    private final List<QuerySort> sorts;
    private final QueryTimeRange time;
    private final ExportRenderOptions renderOptions;
    private final PageRequest page;
    private final Integer limit;
    private final Map<String, Object> payload;
    private final boolean includeExecutionMeta;

    private ExportSpec(Builder builder) {
        super(builder);
        this.operation = builder.operation == null ? ExportOperation.SUBMIT : builder.operation;
        this.format = builder.format;
        this.fileName = builder.fileName;
        this.taskId = builder.taskId;
        this.async = builder.async;
        this.fields = Collections.unmodifiableList(copyStrings(builder.fields));
        this.filters = Collections.unmodifiableList(copyFilters(builder.filters));
        this.sorts = Collections.unmodifiableList(copySorts(builder.sorts));
        this.time = copyTime(builder.time);
        this.renderOptions = copyRenderOptions(builder.renderOptions);
        this.page = copyPage(builder.page);
        this.limit = builder.limit;
        this.payload = Collections.unmodifiableMap(copyPayload(builder.payload));
        this.includeExecutionMeta = builder.includeExecutionMeta;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public ExportOperation getOperation() {
        return operation;
    }

    @Override
    public CrudOperationKey getOperationKey() {
        return CrudOperationKey.of(operation);
    }

    public String getFormat() {
        return format;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTaskId() {
        return taskId;
    }

    public boolean isAsync() {
        return async;
    }

    public List<String> getFields() {
        return copyStrings(fields);
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

    public ExportRenderOptions getRenderOptions() {
        return copyRenderOptions(renderOptions);
    }

    public PageRequest getPage() {
        return copyPage(page);
    }

    public Integer getLimit() {
        return limit;
    }

    public Map<String, Object> getPayload() {
        return copyPayload(payload);
    }

    public boolean isIncludeExecutionMeta() {
        return includeExecutionMeta;
    }

    @Override
    public ExportSpec withSubject(SubjectContext subject) {
        return toBuilder().subject(subject).build();
    }

    @Override
    public ExportSpec withAttributes(Map<String, Object> attributes) {
        return toBuilder().attributes(attributes).build();
    }

    @Override
    public ExportSpec withGovernance(
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

    private Builder copyExportTo(Builder builder) {
        copyBaseTo(builder);
        return builder
            .operation(operation)
            .format(format)
            .fileName(fileName)
            .taskId(taskId)
            .async(async)
            .fields(fields)
            .filters(filters)
            .sorts(sorts)
            .time(time)
            .renderOptions(renderOptions)
            .page(page)
            .limit(limit)
            .payload(payload)
            .includeExecutionMeta(includeExecutionMeta);
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
            target.add(filter == null ? null : new QueryFilter(filter.getField(), filter.getOperator(), filter.getValue()));
        }
        return target;
    }

    private static List<QuerySort> copySorts(List<QuerySort> source) {
        List<QuerySort> target = new ArrayList<QuerySort>();
        if (source == null) {
            return target;
        }
        for (QuerySort sort : source) {
            target.add(sort == null ? null : new QuerySort(sort.getField(), sort.getDirection(), sort.getTarget()));
        }
        return target;
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

    private static ExportRenderOptions copyRenderOptions(ExportRenderOptions source) {
        return source == null ? null : new ExportRenderOptions(source.getTimezone());
    }

    private static PageRequest copyPage(PageRequest source) {
        return source == null ? null : new PageRequest(source.getPage(), source.getLimit());
    }

    private static Map<String, Object> copyPayload(Map<String, Object> source) {
        return source == null ? new HashMap<String, Object>() : new HashMap<String, Object>(source);
    }

    public static final class Builder extends BaseSpec.AbstractBuilder<Builder> {
        private ExportOperation operation = ExportOperation.SUBMIT;
        private String format;
        private String fileName;
        private String taskId;
        private boolean async;
        private List<String> fields = new ArrayList<String>();
        private List<QueryFilter> filters = new ArrayList<QueryFilter>();
        private List<QuerySort> sorts = new ArrayList<QuerySort>();
        private QueryTimeRange time;
        private ExportRenderOptions renderOptions;
        private PageRequest page;
        private Integer limit;
        private Map<String, Object> payload = new HashMap<String, Object>();
        private boolean includeExecutionMeta;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder operation(ExportOperation operation) {
            this.operation = operation == null ? ExportOperation.SUBMIT : operation;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder fields(List<String> fields) {
            this.fields = copyStrings(fields);
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

        public Builder renderOptions(ExportRenderOptions renderOptions) {
            this.renderOptions = copyRenderOptions(renderOptions);
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

        public Builder payload(Map<String, Object> payload) {
            this.payload = copyPayload(payload);
            return this;
        }

        public Builder includeExecutionMeta(boolean includeExecutionMeta) {
            this.includeExecutionMeta = includeExecutionMeta;
            return this;
        }

        public Builder from(ExportSpec source) {
            if (source != null) {
                source.copyExportTo(this);
            }
            return this;
        }

        public ExportSpec build() {
            return new ExportSpec(this);
        }
    }
}
