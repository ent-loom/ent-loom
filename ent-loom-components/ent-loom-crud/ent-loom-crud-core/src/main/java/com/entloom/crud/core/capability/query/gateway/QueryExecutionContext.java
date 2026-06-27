package com.entloom.crud.core.capability.query.gateway;

import com.entloom.crud.core.runtime.router.QueryRoute;
import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.Objects;

/**
 * 查询执行上下文。
 *
 * @param <R> 返回类型
 */
public final class QueryExecutionContext<R> {
    private final QuerySpec<R> requestSpec;
    private final QueryRoute<R> route;
    private final QueryExecutionSpec<R> executionSpec;

    private QueryExecutionContext(QuerySpec<R> requestSpec, QueryRoute<R> route, QueryExecutionSpec<R> executionSpec) {
        this.requestSpec = requestSpec;
        this.route = route;
        this.executionSpec = executionSpec;
    }

    public static <R> QueryExecutionContext<R> create(QuerySpec<R> requestSpec, QueryRoute<R> route) {
        QuerySpec<R> normalizedRequest = Objects.requireNonNull(requestSpec, "requestSpec 不能为空");
        QueryRoute<R> normalizedRoute = Objects.requireNonNull(route, "route 不能为空");
        QueryExecutionSpec<R> execution = QuerySpecMapper.toExecutionSpec(
            normalizedRequest,
            normalizedRoute.handlerDefaultStrategy()
        );
        return new QueryExecutionContext<R>(normalizedRequest, normalizedRoute, execution);
    }

    public QuerySpec<R> requestSpec() {
        return requestSpec;
    }

    public QueryRoute<R> route() {
        return route;
    }

    public QueryExecutionSpec<R> executionSpec() {
        return executionSpec;
    }
}
