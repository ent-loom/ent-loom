package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsSpec;

/**
 * 聚合查询网关。
 */
public interface StatsGateway {
    /**
     * 执行聚合查询。
     *
     * @param spec 查询协议
     * @return 聚合结果
     */
    StatsResult stats(StatsSpec spec);
}
