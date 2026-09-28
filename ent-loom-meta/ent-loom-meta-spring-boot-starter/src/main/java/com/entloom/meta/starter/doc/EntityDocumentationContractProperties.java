package com.entloom.meta.starter.doc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体文档契约服务配置。
 */
@ConfigurationProperties(prefix = "ent.loom.doc.contract")
public class EntityDocumentationContractProperties {
    /** 公共契约服务默认关闭，避免应用无意间暴露实体目录。 */
    private boolean enabled = false;
    /** 文档访问主体与展示范围；与 CRUD 路由及操作权限独立。 */
    private Exposure exposure = new Exposure();
    /** HTTP 入口配置，默认关闭。 */
    private Http http = new Http();

    public Exposure getExposure() {
        return exposure;
    }

    public void setExposure(Exposure exposure) {
        this.exposure = exposure == null ? new Exposure() : exposure;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http == null ? new Http() : http;
    }

    /** 主体必须显式授权；展示范围默认复用 Meta 实体，实体键使用正式资源编码。 */
    public static class Exposure {
        /** 允许访问文档契约的主体标识。 */
        private Set<String> subjectIds = new LinkedHashSet<>();
        /** 包含的实体；空集合表示全部已注册实体。 */
        private Set<String> includeEntities = new LinkedHashSet<>();
        /** 排除的实体，优先于包含清单。 */
        private Set<String> excludeEntities = new LinkedHashSet<>();
        /** 按资源限制字段；未配置的实体展示全部字段，空集合不展示字段。 */
        private Map<String, Set<String>> fields = new LinkedHashMap<>();

        public Set<String> getIncludeEntities() {
            return includeEntities;
        }

        public void setIncludeEntities(Set<String> includeEntities) {
            this.includeEntities = includeEntities == null ? new LinkedHashSet<>() : includeEntities;
        }

        public Set<String> getExcludeEntities() {
            return excludeEntities;
        }

        public void setExcludeEntities(Set<String> excludeEntities) {
            this.excludeEntities = excludeEntities == null ? new LinkedHashSet<>() : excludeEntities;
        }

        public Set<String> getSubjectIds() {
            return subjectIds;
        }

        public void setSubjectIds(Set<String> subjectIds) {
            this.subjectIds = subjectIds == null ? new LinkedHashSet<>() : subjectIds;
        }

        public Map<String, Set<String>> getFields() {
            return fields;
        }

        public void setFields(Map<String, Set<String>> fields) {
            this.fields = fields == null ? new LinkedHashMap<>() : fields;
        }
    }

    /** 实体文档契约 HTTP 入口配置。 */
    public static class Http {
        /** 是否启用文档契约 HTTP 入口。 */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
