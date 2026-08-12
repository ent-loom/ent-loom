package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsSpec;

/**
 * 统计查询执行器。
 */
public interface StatsQueryExecutor {
    /**
     * 执行统计查询。
     */
    StatsResult execute(StatsSpec spec, EntityMeta rootMeta);
}
