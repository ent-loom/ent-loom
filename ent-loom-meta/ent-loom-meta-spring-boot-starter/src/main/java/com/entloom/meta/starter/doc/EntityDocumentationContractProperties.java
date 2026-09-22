package com.entloom.meta.starter.doc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体文档契约服务配置。
 */
@ConfigurationProperties(prefix = "entloom.doc.contract")
public class EntityDocumentationContractProperties {
    /** 公共契约服务默认关闭，避免应用无意间暴露实体目录。 */
    private boolean enabled = false;
    /** 文档暴露白名单；与 CRUD 路由及操作权限独立。 */
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

    /** 未列出的主体、实体和字段均不公开；实体键使用正式资源编码。 */
    public static class Exposure {
        private boolean enabled;
        private Set<String> subjectIds = new LinkedHashSet<>();
        private Map<String, Set<String>> fields = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
