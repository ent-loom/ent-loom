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
        /** 缺省采用身份同名的已注册字段方案；没有方案则失败。 */
        private String fields;
    }

    @Data
    public static class Entity {
        private String policy;
        /** 普通关联字段的例外；未选策略且仅配置此项时，不建立策略绑定。 */
        private List<String> excludeScopeFields = List.of();
        /** 动作目录及其显式 CRUD 映射；空映射可用于仅由授权 DAO 执行的业务操作。 */
        private Map<String, List<String>> operations = new LinkedHashMap<>();
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
