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
    private Map<String, Identity> identities = new LinkedHashMap<>();
    private Map<String, Map<String, Branch>> policies = new LinkedHashMap<>();
    /** 实体业务差异；策略只通过实体或业务接口上的 @EntCrudPolicy 选择。 */
    private Map<String, EntityOptions> entityOptions = new LinkedHashMap<>();
    private Map<String, Relation> relations = new LinkedHashMap<>();
    /** 实体资源码 → 策略身份 → 字段用途；不通过命名方案间接引用。 */
    private Map<String, Map<String, Map<AuthorizationFields.Use, FieldPlan>>> fieldPlans = new LinkedHashMap<>();

    public enum FieldMode {
        /** 仅允许列出的字段。 */
        INCLUDE,
        /** 从元数据允许的字段中排除指定字段。 */
        EXCLUDE,
        /** 允许该用途下元数据允许的全部字段。 */
        ALL,
        /** 不授予该用途的字段权限。 */
        NONE
    }

    @Data
    public static class FieldPlan {
        /** 显式声明时必须选择模式，不根据字段列表推导。 */
        private FieldMode mode;
        /** INCLUDE/EXCLUDE 必须非空；ALL/NONE 不得声明。 */
        private List<String> fields;

        /** 保留 YAML 空标量，由编译阶段报告缺少模式，避免退化成用途缺省。 */
        public static FieldPlan valueOf(String value) {
            if (value != null && value.isEmpty()) return new FieldPlan();
            throw new IllegalArgumentException("字段用途必须配置 mode，不能使用标量或列表简写");
        }
    }

    @Data
    public static class Identity {
        /** 缺省为身份码 + Id。 */
        private String idField;
        /** 显式声明 ID 类型，避免把数字业务标识隐式转换为文本。 */
        private BusinessIdentityIdType idType;
    }

    @Data
    public static class Branch {
        private List<String> actions = List.of();
        /** 缺省采用身份同名的已注册简单关系。 */
        private String scope;
    }

    @Data
    public static class EntityOptions {
        /** 省略按已有关系字段推导；显式列表整体替换推导，空列表关闭推导；不授予权限。 */
        private List<String> ownership;
        /** 省略时从策略标准动作推导；显式配置整体覆盖，动作的空映射仅登记业务能力。 */
        private Map<String, List<String>> operations;
        /** 实体关系字段映射；归属推导和策略范围共用，未声明时复用全局约定。 */
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
