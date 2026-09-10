package com.entloom.meta.starter.doc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体文档契约服务配置。
 */
@ConfigurationProperties(prefix = "entloom.doc.contract")
public class EntityDocumentationContractProperties {
    /** 公共契约服务默认关闭，避免应用无意间暴露实体目录。 */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
