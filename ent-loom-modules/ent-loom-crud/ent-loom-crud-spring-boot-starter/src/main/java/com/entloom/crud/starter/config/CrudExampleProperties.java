package com.entloom.crud.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 演示治理配置；只应在本地示例的配置文件中显式启用。 */
@ConfigurationProperties(prefix = "ent.loom.crud.example")
public class CrudExampleProperties {
    /** 默认关闭，显式开启才装配演示治理。 */
    private boolean enabled;
    /** 演示主体标识；权限与文档授权仍需独立配置。 */
    private String subjectId = "local-developer";
    /** 演示模式使用全量数据范围；可关闭或通过 Bean 替换。 */
    private boolean allowAllDataScope = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public boolean isAllowAllDataScope() {
        return allowAllDataScope;
    }

    public void setAllowAllDataScope(boolean allowAllDataScope) {
        this.allowAllDataScope = allowAllDataScope;
    }

}
