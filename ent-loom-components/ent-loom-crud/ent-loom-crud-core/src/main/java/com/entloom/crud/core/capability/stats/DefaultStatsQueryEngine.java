package com.entloom.crud.core.capability.stats;

import com.entloom.crud.api.enums.StatsOperation;
import com.entloom.crud.core.runtime.engine.EngineCapability;
import com.entloom.crud.core.runtime.engine.EngineFeature;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.DefaultSceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.UnifiedSceneDispatcher;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.core.capability.stats.StatsQueryExecutor;
import com.entloom.crud.core.capability.stats.StatsResult;
import com.entloom.crud.core.capability.stats.StatsSpec;
import java.util.Objects;

/**
 * 默认统计查询引擎，仅承接 stats 查询。
 */
public class DefaultStatsQueryEngine implements StatsQueryEngine {
    /** 默认统计引擎能力声明。 */
    private static final EngineCapability CAPABILITY = EngineCapability.builder("default-stats-query-engine")
        .operations(StatsOperation.QUERY, StatsOperation.PREVIEW)
        .features(
            EngineFeature.STATS_QUERY,
            EngineFeature.ROOT_FILTER,
            EngineFeature.GOVERNANCE_SCOPE,
            EngineFeature.SCENE_ROUTE
        )
        .build();

    private final EntityMetaRegistry metaRegistry;
    private final StatsQueryExecutor statsQueryExecutor;
    private final UnifiedSceneDispatcher<StatsSpec, StatsResult> sceneDispatcher;

    public DefaultStatsQueryEngine(EntityMetaRegistry metaRegistry, StatsQueryExecutor statsQueryExecutor) {
        this(metaRegistry, statsQueryExecutor, new DefaultSceneHandlerRegistry<StatsSpec, StatsResult>());
    }

    public DefaultStatsQueryEngine(
        EntityMetaRegistry metaRegistry,
        StatsQueryExecutor statsQueryExecutor,
        SceneHandlerRegistry<StatsSpec, StatsResult> statsSceneHandlerRegistry
    ) {
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry 不能为空");
        this.statsQueryExecutor = Objects.requireNonNull(statsQueryExecutor, "statsQueryExecutor 不能为空");
        this.sceneDispatcher = new UnifiedSceneDispatcher<StatsSpec, StatsResult>(statsSceneHandlerRegistry);
    }

    @Override
    public EngineCapability capability() {
        return CAPABILITY;
    }

    @Override
    public StatsResult stats(StatsSpec spec) {
        if (spec == null) {
            throw new ValidationException("spec 不能为空");
        }
        if (spec.getPayload() == null) {
            throw new ValidationException("stats 查询必须提供统计 payload");
        }
        capability().requireOperation(spec.getOperationKey());
        capability().requireFeature(EngineFeature.STATS_QUERY, "统计查询");
        final EntityMeta entityMeta = metaRegistry.getEntityMeta(spec.getRootType());
        if (RouteKeyFactory.normalizeScene(spec.getScene()).isEmpty()) {
            return statsQueryExecutor.execute(spec, entityMeta);
        }
        CrudRouteKey routeKey = RouteKeyFactory.buildStatsRoute(spec);
        return sceneDispatcher.dispatch(
            routeKey,
            spec,
            new SceneDelegate<StatsSpec, StatsResult>() {
                @Override
                public StatsResult invoke(StatsSpec delegateSpec) {
                    return statsQueryExecutor.execute(delegateSpec, entityMeta);
                }
            }
        );
    }
}
