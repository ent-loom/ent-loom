package com.entloom.crud.core.runtime.scene;

import com.entloom.crud.core.runtime.router.CrudRouteKey;

/**
 * 统一场景分发器。
 *
 * @param <SPEC> 请求规格类型
 * @param <RESULT> 结果类型
 */
public class UnifiedSceneDispatcher<SPEC, RESULT> {
    private final SceneHandlerRegistry<SPEC, RESULT> registry;

    public UnifiedSceneDispatcher(SceneHandlerRegistry<SPEC, RESULT> registry) {
        this.registry = registry;
    }

    /**
     * 按路由键分发，命中场景处理器则执行处理器；否则执行默认链路。
     */
    public RESULT dispatch(CrudRouteKey routeKey, SPEC spec, SceneDelegate<SPEC, RESULT> delegate) {
        SceneHandler<SPEC, RESULT> sceneHandler = registry == null ? null : registry.resolveOrNull(routeKey);
        if (sceneHandler == null) {
            return delegate.invoke(spec);
        }
        return sceneHandler.handle(spec, delegate);
    }
}

