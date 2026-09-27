package com.entloom.ddl.starter;

import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.enums.GenerationStrategy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DDL starter 配置项。
 */
@ConfigurationProperties(prefix = "ent.loom.ddl")
public class EntDdlProperties {
    /** DDL starter 总开关。 */
    private boolean enabled = false;
    /** 目标数据库 schema；空值使用数据源默认 schema。 */
    private String schema = "";
    /** 是否允许启动时创建数据库。 */
    private boolean createDatabaseIfMissing = false;
    /** DDL 执行级别。 */
    private DdlExecutionMode mode = DdlExecutionMode.NONE;
    /** 扫描实体包。 */
    private List<String> basePackages = new ArrayList<String>();
    /** 直接声明的实体全限定类名。 */
    private List<String> entityClassNames = new ArrayList<String>();
    /** DDL 专属默认值，实体显式声明优先。 */
    private Defaults defaults = new Defaults();

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults == null ? new Defaults() : defaults;
    }

    public static class Defaults {
        /** 未显式声明的主键生成策略。 */
        private GenerationStrategy generationStrategy = GenerationStrategy.UNSET;

        public GenerationStrategy getGenerationStrategy() {
            return generationStrategy;
        }

        public void setGenerationStrategy(GenerationStrategy generationStrategy) {
            this.generationStrategy = generationStrategy == null
                ? GenerationStrategy.UNSET : generationStrategy;
        }
    }

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
