package com.entloom.ddl.starter;

import com.entloom.ddl.api.DdlExecutionMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DDL starter 配置项。
 */
@ConfigurationProperties(prefix = "entloom.ddl")
public class EntDdlProperties {
    private boolean enabled = false;
    private String schema = "";
    private boolean createDatabaseIfMissing = false;
    private DdlExecutionMode mode = DdlExecutionMode.NONE;
    private List<String> basePackages = new ArrayList<String>();
    private List<String> entityClassNames = new ArrayList<String>();

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
        this.basePackages = basePackages == null ? new ArrayList<String>() : new ArrayList<String>(basePackages);
    }

    public List<String> getEntityClassNames() {
        return entityClassNames;
    }

    public void setEntityClassNames(List<String> entityClassNames) {
        this.entityClassNames = entityClassNames == null ? new ArrayList<String>() : new ArrayList<String>(entityClassNames);
    }
}
