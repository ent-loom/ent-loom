package com.entloom.crud.core.capability.query.scene;

import com.entloom.crud.core.runtime.scene.RouteScopedHandler;
import com.entloom.crud.enums.QueryStrategy;

/**
 * Query 场景处理器族接口。
 */
public interface QuerySceneHandler<R> extends RouteScopedHandler {
    /**
     * 处理器声明的默认查询策略。
     */
    default QueryStrategy defaultStrategy() {
        return QueryStrategy.DEFAULT;
    }
}

