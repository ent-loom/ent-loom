package com.entloom.crud.starter.config;

import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.api.access.BusinessIdentityIdType;
import com.entloom.crud.core.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 声明式业务访问与简单身份定义；配置不替代可信认证和归属事实。 */
@Data
@ConfigurationProperties(prefix = "ent.loom.crud.access", ignoreUnknownFields = false)
public class CrudAccessProperties {
    private Map<String, Identity> identities = new LinkedHashMap<>();
    private Map<String, Map<String, Branch>> policies = new LinkedHashMap<>();
    /** 策略名到实体资源码列表；每个实体只能出现在一个绑定列表中。 */
    private Map<String, List<String>> policyBindings = new LinkedHashMap<>();
    /** 实体差异配置；通用 HTTP 默认开放全部已注册实体，无差异时无需登记。 */
    private Map<String, Entity> entities = new LinkedHashMap<>();
    /** 仅声明业务访问差异，不改变 HTTP 暴露；不得与 entities 重复。 */
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

    /** 汇总配置差异供策略编译和归属字段冻结使用，不改变 HTTP 暴露。 */
    public Map<String, EntityOptions> entityDeclarations() {
        Map<String, EntityOptions> declarations = new LinkedHashMap<>(entityOptions);
        entities.forEach((resource, entity) -> {
            if (declarations.putIfAbsent(resource, entity) != null) {
                throw new ValidationException("实体同时出现在 entities 与 entity-options 配置: " + resource);
            }
        });
        return declarations;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Entity extends EntityOptions {
        /** 缺省开放；false 可显式关闭，但不能覆盖实体注解的禁止声明。 */
        private boolean httpExposed = true;

        /** YAML 空标量沿用实体缺省配置；无差异时推荐省略整项。 */
        public static Entity valueOf(String value) {
            if (value != null && value.isEmpty()) return new Entity();
            throw new IllegalArgumentException("实体简写应为空值；差异配置请使用对象结构");
        }
    }

    @Data
    public static class EntityOptions {
        /** 未显式声明时优先使用批量绑定，否则采用资源同名策略；显式声明不得与批量绑定冲突。 */
        private String policy;
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
