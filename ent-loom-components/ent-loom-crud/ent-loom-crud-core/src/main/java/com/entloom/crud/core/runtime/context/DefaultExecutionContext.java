package com.entloom.crud.core.runtime.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 简单执行上下文实现。
 */
@Getter
public class DefaultExecutionContext implements CrudExecutionContext {
    /** 路由键。 */
    private final String routeKey;
    /** 场景标识。 */
    private final String scene;
    /** 开始时间戳（毫秒）。 */
    private final long startTimeMs;
    /** 扩展属性映射。 */
    private final Map<String, Object> attributes;

    public DefaultExecutionContext(String routeKey, String scene) {
        this.routeKey = routeKey;
        this.scene = scene;
        this.startTimeMs = System.currentTimeMillis();
        this.attributes = new HashMap<>();
    }

}
