package com.entloom.ddl.spring;

import com.entloom.ddl.api.DdlExecutionMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spring 侧 DDL 运行选项。
 */
public final class EntDdlSpringOptions {
    private boolean enabled = false;
    private String schema = "";
    private boolean createDatabaseIfMissing = false;
    private DdlExecutionMode mode = DdlExecutionMode.NONE;
    private List<String> basePackages = Collections.emptyList();
    private List<Class<?>> entityClasses = Collections.emptyList();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema == null ? "" : schema.trim();
    }

    public boolean isCreateDatabaseIfMissing() {
        return createDatabaseIfMissing;
    }

    public void setCreateDatabaseIfMissing(boolean createDatabaseIfMissing) {
        this.createDatabaseIfMissing = createDatabaseIfMissing;
    }

    public DdlExecutionMode getMode() {
        return mode;
    }

    public void setMode(DdlExecutionMode mode) {
        this.mode = mode == null ? DdlExecutionMode.NONE : mode;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = immutableCopy(basePackages);
    }

    public List<Class<?>> getEntityClasses() {
        return entityClasses;
    }

    public void setEntityClasses(List<Class<?>> entityClasses) {
        this.entityClasses = immutableCopyClasses(entityClasses);
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
