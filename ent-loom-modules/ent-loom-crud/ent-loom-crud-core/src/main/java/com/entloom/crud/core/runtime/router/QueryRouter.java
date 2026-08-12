package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * 查询路由器。
 */
public interface QueryRouter {
    /**
     * 路由查询处理器。
     *
     * @param spec 查询 spec
     * @param <R> 返回类型
     * @return 路由结果
     */
    <R> QueryRoute<R> route(QuerySpec<R> spec);
}
