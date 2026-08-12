package com.entloom.ddl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 元数据加载请求。
 */
public final class MetadataLoadRequest {
    private final List<String> basePackages;
    private final List<Class<?>> entityClasses;

    public MetadataLoadRequest(List<String> basePackages, List<Class<?>> entityClasses) {
        this.basePackages = immutableCopy(basePackages);
        this.entityClasses = immutableCopyClasses(entityClasses);
    }

    public List<String> basePackages() {
        return basePackages;
    }

    public List<Class<?>> entityClasses() {
        return entityClasses;
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    private static List<Class<?>> immutableCopyClasses(List<Class<?>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Class<?>>(source));
    }
}
