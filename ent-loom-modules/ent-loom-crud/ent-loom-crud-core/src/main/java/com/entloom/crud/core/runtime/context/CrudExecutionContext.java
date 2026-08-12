package com.entloom.crud.core.runtime.context;

import java.util.Map;

/**
 * 执行上下文。
 */
public interface CrudExecutionContext {
    /**
     * 路由 key。
     *
     * @return routeKey
     */
    String getRouteKey();

    /**
     * 场景 code。
     *
     * @return scene
     */
    String getScene();

    /**
     * 开始时间戳。
     *
     * @return 时间戳
     */
    long getStartTimeMs();

    /**
     * 扩展属性。
     *
     * @return 属性 map
     */
    Map<String, Object> getAttributes();

}
