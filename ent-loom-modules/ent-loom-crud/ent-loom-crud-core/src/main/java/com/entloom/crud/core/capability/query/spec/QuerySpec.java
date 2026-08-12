package com.entloom.crud.core.capability.query.spec;

import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.api.model.QueryTimeRange;
import com.entloom.crud.enums.QueryStrategy;
import com.entloom.crud.core.runtime.spec.BaseSpec;
import com.entloom.crud.core.runtime.spec.FilterableSpec;
import com.entloom.crud.core.runtime.spec.OperationKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 查询请求协议对象（不可变）。
 *
 * @param <R> 返回类型
 */
public class QuerySpec<R> extends BaseSpec implements FilterableSpec, OperationKeySpec {
    /** 操作类型。 */
    private final QueryOperation op;
    /** 查询策略。 */
    private final QueryStrategy strategy;
    /** 处理器声明默认查询策略。 */
    private final QueryStrategy handlerDefaultStrategy;
    /** 过滤条件列表。 */
    private final List<QueryFilter> filters;
    /** 排序条件列表。 */
    private final List<QuerySort> sorts;
    /** 时间范围。 */
    private final QueryTimeRange time;
    /** 分页请求。 */
    private final PageRequest page;
    /** 分页总数策略。 */
    private final PageCountMode countMode;
    /** 分页大小。 */
    private final Integer limit;
    /** 选择字段列表。 */
    private final List<String> selectFields;
    /** 展开关联列表。 */
    private final List<String> expandRelations;
    /** 结果类型。 */
    private final Class<R> resultType;

    protected <B extends AbstractBuilder<R, B>> QuerySpec(AbstractBuilder<R, B> builder) {
        super(builder);
        this.op = builder.op;
        this.strategy = builder.strategy == null ? QueryStrategy.DEFAULT : builder.strategy;
        this.handlerDefaultStrategy = builder.handlerDefaultStrategy == null
            ? QueryStrategy.DEFAULT
            : builder.handlerDefaultStrategy;
        this.filters = Collections.unmodifiableList(copyFilters(builder.filters));
        this.sorts = Collections.unmodifiableList(copySorts(builder.sorts));
        this.time = copyTime(builder.time);
        this.page = copyPage(builder.page);
        this.countMode = builder.countMode == null ? PageCountMode.EXACT : builder.countMode;
        this.limit = builder.limit;
        this.selectFields = Collections.unmodifiableList(copyStrings(builder.selectFields));
        this.expandRelations = Collections.unmodifiableList(copyStrings(builder.expandRelations));
        this.resultType = builder.resultType;
    }

    public static <R> Builder<R> builder() {
        return new Builder<R>();
    }

    public Builder<R> toBuilder() {
        return new Builder<R>().from(this);
    }

    protected final <B extends AbstractBuilder<R, B>> B copyQueryTo(B builder) {
        return copyBaseTo(builder)
            .op(op)
            .strategy(strategy)
            .handlerDefaultStrategy(handlerDefaultStrategy)
            .filters(getFilters())
            .sorts(getSorts())
            .time(getTime())
            .page(getPage())
            .countMode(countMode)
            .limit(limit)
            .selectFields(getSelectFields())
            .expandRelations(getExpandRelations())
            .resultType(resultType);
    }

    public final QueryOperation getOp() {
        return op;
    }

    public final CrudOperationKey getOperationKey() {
        return CrudOperationKey.of(op);
    }

    public final QueryStrategy getStrategy() {
        return strategy;
    }

    public final QueryStrategy getHandlerDefaultStrategy() {
        return handlerDefaultStrategy;
    }

    public List<QueryFilter> getFilters() {
        return copyFilters(filters);
    }

    public List<QuerySort> getSorts() {
        return copySorts(sorts);
    }

    public QueryTimeRange getTime() {
        return copyTime(time);
    }

    public PageRequest getPage() {
        return copyPage(page);
    }

    public final PageCountMode getCountMode() {
        return countMode;
    }

    public final Integer getLimit() {
        return limit;
    }

    public List<String> getSelectFields() {
        return copyStrings(selectFields);
    }

    public List<String> getExpandRelations() {
        return copyStrings(expandRelations);
    }

    public final Class<R> getResultType() {
        return resultType;
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

    public static class Builder<R> extends AbstractBuilder<R, Builder<R>> {
        @Override
        protected Builder<R> self() {
            return this;
        }

        @Override
        public QuerySpec<R> build() {
            return new QuerySpec<R>(this);
        }
    }

    protected abstract static class AbstractBuilder<R, B extends AbstractBuilder<R, B>> extends BaseSpec.AbstractBuilder<B> {
        private QueryOperation op;
        private QueryStrategy strategy = QueryStrategy.DEFAULT;
        private QueryStrategy handlerDefaultStrategy = QueryStrategy.DEFAULT;
        private List<QueryFilter> filters = new ArrayList<QueryFilter>();
        private List<QuerySort> sorts = new ArrayList<QuerySort>();
        private QueryTimeRange time;
        private PageRequest page;
        private PageCountMode countMode = PageCountMode.EXACT;
        private Integer limit;
        private List<String> selectFields = new ArrayList<String>();
        private List<String> expandRelations = new ArrayList<String>();
        private Class<R> resultType;

        public B op(QueryOperation op) {
            this.op = op;
            return self();
        }

        public B strategy(QueryStrategy strategy) {
            this.strategy = strategy == null ? QueryStrategy.DEFAULT : strategy;
            return self();
        }

        public B handlerDefaultStrategy(QueryStrategy handlerDefaultStrategy) {
            this.handlerDefaultStrategy = handlerDefaultStrategy == null
                ? QueryStrategy.DEFAULT
                : handlerDefaultStrategy;
            return self();
        }

        public B filters(List<QueryFilter> filters) {
            this.filters = copyFilters(filters);
            return self();
        }

        public B sorts(List<QuerySort> sorts) {
            this.sorts = copySorts(sorts);
            return self();
        }

        public B time(QueryTimeRange time) {
            this.time = copyTime(time);
            return self();
        }

        public B page(PageRequest page) {
            this.page = copyPage(page);
            return self();
        }

        public B countMode(PageCountMode countMode) {
            this.countMode = countMode == null ? PageCountMode.EXACT : countMode;
            return self();
        }

        public B limit(Integer limit) {
            this.limit = limit;
            return self();
        }

        public B selectFields(List<String> selectFields) {
            this.selectFields = copyStrings(selectFields);
            return self();
        }

        public B expandRelations(List<String> expandRelations) {
            this.expandRelations = copyStrings(expandRelations);
            return self();
        }

        public B resultType(Class<R> resultType) {
            this.resultType = resultType;
            return self();
        }

        public B from(QuerySpec<R> source) {
            if (source != null) {
                source.copyQueryTo(self());
            }
            return self();
        }

        public abstract QuerySpec<R> build();
    }
}
