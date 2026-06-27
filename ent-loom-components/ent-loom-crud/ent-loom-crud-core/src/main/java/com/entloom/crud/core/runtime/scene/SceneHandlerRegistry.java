package com.entloom.crud.core.runtime.scene;

import com.entloom.crud.core.runtime.router.CrudRouteKey;

/**
 * 场景处理器注册表。
 *
 * @param <SPEC> 请求规格类型
 * @param <RESULT> 结果类型
 */
public interface SceneHandlerRegistry<SPEC, RESULT> {
    /**
     * 注册处理器。
     */
    void register(SceneHandler<SPEC, RESULT> handler);

    /**
     * 解析处理器，未命中返回 null。
     */
    SceneHandler<SPEC, RESULT> resolveOrNull(CrudRouteKey routeKey);
}

