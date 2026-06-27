package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.exception.RouteAmbiguousException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stats payload 定制器注册表。
 */
public class StatsPayloadCustomizerRegistry {
    private final Map<CrudRouteKey, StatsPayloadCustomizer> customizers =
        new LinkedHashMap<CrudRouteKey, StatsPayloadCustomizer>();

    public synchronized void register(StatsPayloadCustomizer customizer) {
        if (customizer == null) {
            throw new ValidationException("customizer 不能为空");
        }
        Set<CrudRouteKey> routeKeys = customizer.routeKeys();
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空");
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空");
            }
            if (customizers.containsKey(routeKey)) {
                throw new RouteAmbiguousException("payload customizer 路由重复注册: " + routeKey);
            }
            customizers.put(routeKey, customizer);
        }
    }

    public synchronized StatsPayloadCustomizer resolveOrNull(CrudRouteKey routeKey) {
        if (routeKey == null) {
            return null;
        }
        return customizers.get(routeKey);
    }
}

