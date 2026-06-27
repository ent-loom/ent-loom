package com.entloom.crud.core.runtime.scene;

import com.entloom.crud.core.runtime.router.CrudRouteKey;
import java.util.Set;

/**
 * 声明路由范围的处理器。
 */
public interface RouteScopedHandler {
    /**
     * 返回处理器声明支持的路由键集合。
     */
    Set<CrudRouteKey> routeKeys();
}

