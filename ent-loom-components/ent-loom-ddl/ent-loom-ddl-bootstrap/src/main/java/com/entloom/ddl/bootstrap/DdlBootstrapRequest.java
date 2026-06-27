package com.entloom.ddl.bootstrap;

import com.entloom.ddl.api.DdlExecutionMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 无 Spring 启动入口请求参数。
 */
public final class DdlBootstrapRequest {
    private final String schema;
    private final boolean createDatabaseIfMissing;
    private final DdlExecutionMode mode;
    private final List<String> basePackages;
    private final List<Class<?>> entityClasses;

    public DdlBootstrapRequest(String schema,
                               boolean createDatabaseIfMissing,
                               DdlExecutionMode mode,
                               List<String> basePackages,
                               List<Class<?>> entityClasses) {
        this.schema = schema == null ? "" : schema.trim();
        this.createDatabaseIfMissing = createDatabaseIfMissing;
        this.mode = mode == null ? DdlExecutionMode.NONE : mode;
        this.basePackages = immutableCopy(basePackages);
        this.entityClasses = immutableCopyClasses(entityClasses);
    }

    public String schema() {
        return schema;
    }

    public boolean createDatabaseIfMissing() {
        return createDatabaseIfMissing;
    }

    public DdlExecutionMode mode() {
        return mode;
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
