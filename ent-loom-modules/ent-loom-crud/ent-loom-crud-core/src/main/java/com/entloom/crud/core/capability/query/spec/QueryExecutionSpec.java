package com.entloom.crud.core.capability.query.spec;

/**
 * 查询执行协议对象（执行态）。
 *
 * @param <R> 返回类型
 */
public final class QueryExecutionSpec<R> extends QuerySpec<R> {
    private QueryExecutionSpec(Builder<R> builder) {
        super(builder);
    }

    public static <R> Builder<R> executionBuilder() {
        return new Builder<R>();
    }

    public static <R> QueryExecutionSpec<R> from(QuerySpec<R> source) {
        return QueryExecutionSpec.<R>executionBuilder().from(source).build();
    }

    public static final class Builder<R> extends QuerySpec.AbstractBuilder<R, Builder<R>> {
        @Override
        protected Builder<R> self() {
            return this;
        }

        @Override
        public QueryExecutionSpec<R> build() {
            return new QueryExecutionSpec<R>(this);
        }
    }
}
