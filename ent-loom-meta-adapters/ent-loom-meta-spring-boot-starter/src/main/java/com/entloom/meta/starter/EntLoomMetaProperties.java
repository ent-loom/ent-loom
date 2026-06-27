package com.entloom.meta.starter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Meta -> sub-framework adapter auto-configuration properties.
 */
@ConfigurationProperties(prefix = "ent.loom.meta")
public class EntLoomMetaProperties {
    private boolean enabled = true;
    private List<String> basePackages = new ArrayList<String>();
    private List<String> entityClassNames = new ArrayList<String>();
    private Crud crud = new Crud();
    private Doc doc = new Doc();
    private Diagnostics diagnostics = new Diagnostics();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = nonBlankList(basePackages);
    }

    public List<String> getEntityClassNames() {
        return entityClassNames;
    }

    public void setEntityClassNames(List<String> entityClassNames) {
        this.entityClassNames = nonBlankList(entityClassNames);
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
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Doc {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Diagnostics {
        private boolean failFast = true;

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }
}
