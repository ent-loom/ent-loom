package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.runtime.scene.RouteScopedHandler;
import com.entloom.crud.core.capability.stats.StatsQueryPayload;

/**
 * Stats payload 定制器。
 */
public interface StatsPayloadCustomizer extends RouteScopedHandler {
    /**
     * 定制 stats payload。
     */
    StatsQueryPayload customize(StatsPayloadCustomizerContext context);
}

