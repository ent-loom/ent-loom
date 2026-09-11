package com.entloom.meta.starter.doc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体文档契约服务配置。
 */
@ConfigurationProperties(prefix = "entloom.doc.contract")
public class EntityDocumentationContractProperties {
    /** 公共契约服务默认关闭，避免应用无意间暴露实体目录。 */
    private boolean enabled = false;
    /** HTTP 入口配置，默认关闭。 */
    private Http http = new Http();

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
