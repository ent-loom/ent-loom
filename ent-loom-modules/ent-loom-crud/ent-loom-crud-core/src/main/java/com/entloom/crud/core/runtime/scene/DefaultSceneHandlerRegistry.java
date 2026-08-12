package com.entloom.crud.core.runtime.scene;

import com.entloom.crud.core.exception.RouteAmbiguousException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 默认场景处理器注册表实现。
 */
public class DefaultSceneHandlerRegistry<SPEC, RESULT> implements SceneHandlerRegistry<SPEC, RESULT> {
    private final Map<CrudRouteKey, SceneHandler<SPEC, RESULT>> handlers = new LinkedHashMap<CrudRouteKey, SceneHandler<SPEC, RESULT>>();

    @Override
    public synchronized void register(SceneHandler<SPEC, RESULT> handler) {
        if (handler == null) {
            throw new ValidationException("handler 不能为空");
        }
        Set<CrudRouteKey> routeKeys = handler.routeKeys();
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空");
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空");
            }
            if (handlers.containsKey(routeKey)) {
                throw new RouteAmbiguousException("路由重复注册: " + routeKey);
            }
            handlers.put(routeKey, handler);
        }
    }

    @Override
    public synchronized SceneHandler<SPEC, RESULT> resolveOrNull(CrudRouteKey routeKey) {
        if (routeKey == null) {
            return null;
        }
        return handlers.get(routeKey);
    }
}

