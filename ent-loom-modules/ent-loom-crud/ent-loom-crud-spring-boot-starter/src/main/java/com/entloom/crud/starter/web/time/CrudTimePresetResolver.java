package com.entloom.crud.starter.web.time;

import java.time.ZoneId;

/**
 * 时间预设解析器。
 */
public interface CrudTimePresetResolver {
    /**
     * 尝试把 preset 解析为时间范围。
     * 返回 null 表示当前解析器不支持该 preset。
     */
    CrudTimeRange resolve(String preset, ZoneId zoneId);

    /**
     * 解析优先级（值越小优先级越高）。
     */
    default int order() {
        return 0;
    }
}
