package com.entloom.crud.core.repository;

import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.enums.SortTarget;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单实体结构化查询条件。
 *
 * @param <T> 实体类型
 */
public final class EntityQuery<T> {
    /** 过滤条件。 */
    private final List<QueryFilter> filters;
    /** 排序条件。 */
    private final List<QuerySort> sorts;
    /** 分页请求。 */
    private final PageRequest page;
    /** 列表最大返回条数。 */
    private final Integer limit;
    /** 分页总数策略。 */
    private final PageCountMode countMode;
    /** 查询字段。 */
    private final List<String> selectFields;
    /** 展开的实体关系。 */
    private final List<String> expandRelations;

    private EntityQuery(Builder<T> builder) {
        this.filters = Collections.unmodifiableList(new ArrayList<QueryFilter>(builder.filters));
        this.sorts = Collections.unmodifiableList(new ArrayList<QuerySort>(builder.sorts));
        this.page = builder.page == null ? null : new PageRequest(builder.page.getPage(), builder.page.getLimit());
        this.limit = builder.limit;
        this.countMode = builder.countMode;
        this.selectFields = Collections.unmodifiableList(new ArrayList<String>(builder.selectFields));
        this.expandRelations = Collections.unmodifiableList(new ArrayList<String>(builder.expandRelations));
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public static <T> EntityQuery<T> empty() {
        return EntityQuery.<T>builder().build();
    }

    public List<QueryFilter> getFilters() {
        return new ArrayList<QueryFilter>(filters);
    }

    public List<QuerySort> getSorts() {
        return new ArrayList<QuerySort>(sorts);
    }

    public PageRequest getPage() {
        return page == null ? null : new PageRequest(page.getPage(), page.getLimit());
    }

    public Integer getLimit() {
        return limit;
    }

    public PageCountMode getCountMode() {
        return countMode;
    }

    public List<String> getSelectFields() {
        return new ArrayList<String>(selectFields);
    }

    public List<String> getExpandRelations() {
        return new ArrayList<String>(expandRelations);
    }

    /** 实体查询构建器。 */
    public static final class Builder<T> {
        private final List<QueryFilter> filters = new ArrayList<QueryFilter>();
        private final List<QuerySort> sorts = new ArrayList<QuerySort>();
        private PageRequest page;
        private Integer limit;
        private PageCountMode countMode = PageCountMode.EXACT;
        private final List<String> selectFields = new ArrayList<String>();
        private final List<String> expandRelations = new ArrayList<String>();

        public Builder<T> filter(String field, FilterOperator operator, Object value) {
            requireField(field);
            if (operator == null) {
                throw new ValidationException("过滤操作符不能为空");
            }
            filters.add(new QueryFilter(field.trim(), operator, value));
            return this;
        }

        public Builder<T> eq(String field, Object value) {
            return filter(field, FilterOperator.EQ, value);
        }

        public Builder<T> ne(String field, Object value) {
            return filter(field, FilterOperator.NE, value);
        }

        public Builder<T> gt(String field, Object value) {
            return filter(field, FilterOperator.GT, value);
        }

        public Builder<T> ge(String field, Object value) {
            return filter(field, FilterOperator.GE, value);
        }

        public Builder<T> lt(String field, Object value) {
            return filter(field, FilterOperator.LT, value);
        }

        public Builder<T> le(String field, Object value) {
            return filter(field, FilterOperator.LE, value);
        }

        public Builder<T> in(String field, Object values) {
            return filter(field, FilterOperator.IN, values);
        }

        public Builder<T> notIn(String field, Object values) {
            return filter(field, FilterOperator.NOT_IN, values);
        }

        public Builder<T> between(String field, Object range) {
            return filter(field, FilterOperator.BETWEEN, range);
        }

        public Builder<T> like(String field, Object value) {
            return filter(field, FilterOperator.LIKE, value);
        }

        public Builder<T> isNull(String field) {
            return filter(field, FilterOperator.IS_NULL, null);
        }

        public Builder<T> isNotNull(String field) {
            return filter(field, FilterOperator.IS_NOT_NULL, null);
        }

        public Builder<T> orderBy(String field, SortDirection direction) {
            requireField(field);
            if (direction == null) {
                throw new ValidationException("排序方向不能为空");
            }
            sorts.add(new QuerySort(field.trim(), direction, SortTarget.FIELD));
            return this;
        }

        public Builder<T> orderByAsc(String field) {
            return orderBy(field, SortDirection.ASC);
        }

        public Builder<T> orderByDesc(String field) {
            return orderBy(field, SortDirection.DESC);
        }

        public Builder<T> page(int pageNumber, int pageSize) {
            this.page = new PageRequest(pageNumber, pageSize);
            return this;
        }

        public Builder<T> limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder<T> countMode(PageCountMode countMode) {
            this.countMode = countMode == null ? PageCountMode.EXACT : countMode;
            return this;
        }

        public Builder<T> select(String... fields) {
            addFields(selectFields, fields);
            return this;
        }

        public Builder<T> expand(String... relations) {
            addFields(expandRelations, relations);
            return this;
        }

        public EntityQuery<T> build() {
            return new EntityQuery<T>(this);
        }

        private void addFields(List<String> target, String[] fields) {
            if (fields == null) {
                return;
            }
            for (String field : fields) {
                requireField(field);
                target.add(field.trim());
            }
        }

        private void requireField(String field) {
            if (field == null || field.trim().isEmpty()) {
                throw new ValidationException("字段名不能为空");
            }
        }
    }
}
