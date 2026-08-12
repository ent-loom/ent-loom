package com.entloom.crud.core.capability.query.gateway;

import com.entloom.crud.core.capability.query.spec.QueryExecutionSpec;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.enums.QueryStrategy;

/**
 * QuerySpec 执行态复制器。
 */
public final class QuerySpecMapper {
    private QuerySpecMapper() {
    }

    public static <R> QueryExecutionSpec<R> toExecutionSpec(QuerySpec<R> source, QueryStrategy handlerDefaultStrategy) {
        return QueryExecutionSpec.<R>executionBuilder()
            .from(source)
            .handlerDefaultStrategy(handlerDefaultStrategy)
            .build();
    }
}
