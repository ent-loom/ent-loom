package com.entloom.crud.starter.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 演示治理配置；只应在本地示例的配置文件中显式启用。 */
@ConfigurationProperties(prefix = "ent.loom.crud.example")
public class CrudExampleProperties {
    /** 默认关闭，显式开启才装配演示治理。 */
    private boolean enabled;
    /** 演示主体标识；权限与文档授权仍需独立配置。 */
    private String subjectId = "local-developer";
    /** 演示模式的 Gateway 与 DAO 使用全量数据范围；可关闭或分别通过 Bean 替换。 */
    private boolean allowAllDataScope = true;
    /** 静态令牌账号；每项仅含 token、subject-id 和一个约定身份 ID 字段。 */
    private List<Map<String, String>> accounts = new ArrayList<>();
    /** 未命中路由映射的服务端入口。 */
    private String defaultAccessEntry = "default";
    /** 服务端路径模式到入口的映射；同一路径命中不同入口时拒绝。 */
    private Map<String, String> routes = new LinkedHashMap<>();

    public List<Map<String, String>> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Map<String, String>> accounts) {
        this.accounts = accounts;
    }

    public String getDefaultAccessEntry() {
        return defaultAccessEntry;
    }

    public void setDefaultAccessEntry(String defaultAccessEntry) {
        this.defaultAccessEntry = defaultAccessEntry;
    }

    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

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
