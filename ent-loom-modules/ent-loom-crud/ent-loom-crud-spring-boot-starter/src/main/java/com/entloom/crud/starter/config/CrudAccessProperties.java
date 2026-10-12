package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.api.access.BusinessIdentityIdType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 声明式业务访问与简单身份定义；配置不替代可信认证和归属事实。 */
@Data
@ConfigurationProperties(prefix = "ent.loom.crud.access", ignoreUnknownFields = false)
public class CrudAccessProperties {
    private Defaults defaults = new Defaults();
    private Map<String, Identity> identities = new LinkedHashMap<>();
    private Map<String, Map<String, Branch>> policies = new LinkedHashMap<>();
    /** 策略名到实体资源码列表；每个实体只能出现在一个绑定列表中。 */
    private Map<String, List<String>> policyBindings = new LinkedHashMap<>();
    private Map<String, Entity> entities = new LinkedHashMap<>();
    private Map<String, Relation> relations = new LinkedHashMap<>();
    private Map<String, Map<AuthorizationFields.Use, List<String>>> fieldPlans = new LinkedHashMap<>();

    @Data
    public static class Identity {
        /** 缺省为身份码 + Id。 */
        private String idField;
        /** 显式声明 ID 类型，避免把数字业务标识隐式转换为文本。 */
        private BusinessIdentityIdType idType;
    }

    @Data
    public static class Defaults {
        /** 仅匹配实体中实际存在的持久化字段；不授予权限或自动补充查询范围。 */
        private List<String> scopeFields = List.of();
    }

    @Data
    public static class Branch {
        private List<String> actions = List.of();
        /** 缺省采用身份同名的已注册简单关系。 */
        private String scope;
    }

    @Data
    public static class Entity {
        /** 未显式声明时优先使用批量绑定，否则采用资源同名策略；显式声明不得与批量绑定冲突。 */
        private String policy;
        /** 按策略身份分支选择字段方案；未声明时复用实体同名方案，否则采用字段缺省。 */
        private Map<String, String> fields = new LinkedHashMap<>();
        /** 普通关联字段的例外；未选策略且仅配置此项时，不建立策略绑定。 */
        private List<String> excludeScopeFields = List.of();
        /** 省略时从策略标准动作推导；显式配置整体覆盖，动作的空映射仅登记业务能力。 */
        private Map<String, List<String>> operations;
        /** 实体关系字段例外，未声明时复用全局关系约定。 */
        private Map<String, String> relations = new LinkedHashMap<>();
    }

    @Data
    public static class Relation {
        /** 缺省为关系名称 + Id。 */
        private String field;
        /** 缺省为关系名称 + Id，读取已核验身份的属性。 */
        private String identityField;
    }
}
