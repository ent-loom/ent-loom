package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.query.handler.QueryHandler;
import com.entloom.crud.enums.QueryStrategy;

/**
 * 查询路由结果。
 *
 * @param <R> 返回类型
 */
public interface QueryRoute<R> {
    /**
     * 取得处理器。
     *
     * @return 查询处理器
     */
    QueryHandler<R> handler();

    /**
     * 处理器声明默认查询策略。
     *
     * @return 默认策略；未声明时返回 DEFAULT
     */
    default QueryStrategy handlerDefaultStrategy() {
        return QueryStrategy.DEFAULT;
    }
}
