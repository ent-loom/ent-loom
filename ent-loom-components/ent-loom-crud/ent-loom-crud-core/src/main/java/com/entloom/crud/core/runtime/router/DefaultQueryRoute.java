package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.query.handler.QueryHandler;
import com.entloom.crud.enums.QueryStrategy;

/**
 * 简单查询路由结果。
 *
 * @param <R> 返回类型
 */
public class DefaultQueryRoute<R> implements QueryRoute<R> {
    /** 处理器。 */
    private final QueryHandler<R> handler;
    /** 处理器声明默认策略。 */
    private final QueryStrategy handlerDefaultStrategy;

    public DefaultQueryRoute(QueryHandler<R> handler) {
        this(handler, QueryStrategy.DEFAULT);
    }

    public DefaultQueryRoute(QueryHandler<R> handler, QueryStrategy handlerDefaultStrategy) {
        this.handler = handler;
        this.handlerDefaultStrategy = handlerDefaultStrategy == null ? QueryStrategy.DEFAULT : handlerDefaultStrategy;
    }

    @Override
    public QueryHandler<R> handler() {
        return handler;
    }

    @Override
    public QueryStrategy handlerDefaultStrategy() {
        return handlerDefaultStrategy;
    }
}
