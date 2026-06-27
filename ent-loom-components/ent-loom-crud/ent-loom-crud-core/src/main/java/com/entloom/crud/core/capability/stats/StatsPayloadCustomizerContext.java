package com.entloom.crud.core.capability.stats;

import com.entloom.crud.core.capability.stats.StatsQueryPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stats payload 定制上下文。
 */
public final class StatsPayloadCustomizerContext {
    private final Class<?> rootType;
    private final List<Class<?>> entityClasses;
    private final String scene;
    private final StatsQueryPayload payload;

    public StatsPayloadCustomizerContext(
        Class<?> rootType,
        List<Class<?>> entityClasses,
        String scene,
        StatsQueryPayload payload
    ) {
        this.rootType = rootType;
        this.entityClasses = entityClasses == null
            ? Collections.<Class<?>>emptyList()
            : Collections.unmodifiableList(new ArrayList<Class<?>>(entityClasses));
        this.scene = scene;
        this.payload = payload;
    }

    public Class<?> getRootType() {
        return rootType;
    }

    public List<Class<?>> getEntityClasses() {
        return entityClasses;
    }

    public String getScene() {
        return scene;
    }

    public StatsQueryPayload getPayload() {
        return payload;
    }
}

