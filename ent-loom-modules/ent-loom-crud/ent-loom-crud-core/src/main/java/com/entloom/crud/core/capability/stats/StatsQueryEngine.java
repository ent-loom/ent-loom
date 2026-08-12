package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.runtime.engine.EngineCapability;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsSpec;

/**
 * 聚合查询能力接口。
 */
public interface StatsQueryEngine {
    default EngineCapability capability() {
        return EngineCapability.unknown(getClass().getName());
    }

    StatsResult stats(StatsSpec spec);
}
