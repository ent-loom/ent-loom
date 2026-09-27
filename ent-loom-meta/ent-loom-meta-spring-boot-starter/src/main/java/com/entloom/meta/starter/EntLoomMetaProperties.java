package com.entloom.meta.starter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Meta -> sub-framework adapter auto-configuration properties.
 */
@ConfigurationProperties(prefix = "ent.loom.meta")
public class EntLoomMetaProperties {
    /** Meta 适配器总开关。 */
    private boolean enabled = true;
    /** 参与 Meta 投影的实体全限定类名；未提供自定义目录适配器时使用。 */
    private List<String> entityClassNames = new ArrayList<String>();
    /** 递归扫描实体的包名，识别 EntEntity、EntCrudEntity、EntDocEntity。 */
    private List<String> basePackages = new ArrayList<String>();
    /** Meta 到 CRUD 的适配开关。 */
    private Crud crud = new Crud();
    /** Meta 到 DOC 的适配开关。 */
    private Doc doc = new Doc();
    /** 元数据诊断策略。 */
    private Diagnostics diagnostics = new Diagnostics();
    /** Meta 实体默认值。 */
    private Defaults defaults = new Defaults();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getEntityClassNames() {
        return entityClassNames;
    }

    public void setEntityClassNames(List<String> entityClassNames) {
        this.entityClassNames = nonBlankList(entityClassNames);
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = nonBlankList(basePackages);
    }

    public Crud getCrud() {
        return crud;
    }

    public void setCrud(Crud crud) {
        this.crud = crud == null ? new Crud() : crud;
    }

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc == null ? new Doc() : doc;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics == null ? new Diagnostics() : diagnostics;
    }

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults == null ? new Defaults() : defaults;
    }

    private static List<String> nonBlankList(List<String> values) {
        List<String> result = new ArrayList<String>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class Crud {
        /** 是否启用 Meta 到 CRUD 的适配器。 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Doc {
        /** 是否启用 Meta 到 DOC 的适配器。 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Diagnostics {
        /** 发现元数据错误时是否快速失败。 */
        private boolean failFast = true;

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }

    public static class Defaults {
        /** 未显式声明时的实体所属服务。 */
        private String service;

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = trimToNull(service);
        }
    }
}
