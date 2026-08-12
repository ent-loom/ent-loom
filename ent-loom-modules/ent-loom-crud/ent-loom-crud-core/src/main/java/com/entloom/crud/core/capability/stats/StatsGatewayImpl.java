package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.capability.stats.StatsQueryEngine;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.Objects;

/**
 * 默认聚合查询网关实现。
 */
public class StatsGatewayImpl implements StatsGateway {
    private final StatsQueryEngine statsQueryEngine;
    private final ExecutionPipeline executionPipeline;

    public StatsGatewayImpl(StatsQueryEngine statsQueryEngine, ExecutionPipeline executionPipeline) {
        this.statsQueryEngine = Objects.requireNonNull(statsQueryEngine, "statsQueryEngine 不能为空");
        this.executionPipeline = Objects.requireNonNull(executionPipeline, "executionPipeline 不能为空");
    }

    @Override
    public StatsResult stats(StatsSpec spec) {
        return executionPipeline.execute(
            () -> prepareRequestSpec(spec),
            requestSpec -> executionPipeline.governStats(requestSpec),
            (requestSpec, governedStatsSpec, governance) -> statsQueryEngine.stats(governedStatsSpec)
        );
    }

    private StatsSpec prepareRequestSpec(StatsSpec spec) {
        return Objects.requireNonNull(spec, "spec 不能为空").toBuilder().build();
    }
}
