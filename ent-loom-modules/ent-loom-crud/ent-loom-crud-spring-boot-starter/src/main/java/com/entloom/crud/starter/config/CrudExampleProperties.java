package com.entloom.crud.starter.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 演示治理配置；只应在本地示例的配置文件中显式启用。 */
@Getter
@Setter
public class CrudExampleProperties {
    /** 默认关闭，显式开启才装配演示治理。 */
    private boolean enabled;
    /** 演示主体标识；权限与文档授权仍需独立配置。 */
    private String subjectId = "local-developer";
    /** 演示模式的 Gateway 与 DAO 使用全量数据范围；可关闭或分别通过 Bean 替换。 */
    private boolean allowAllDataScope = true;
    /** 静态令牌账号；身份码和 ID 使用固定结构，类型由共享身份定义校验。 */
    private List<Account> accounts = new ArrayList<>();
    /** 未命中路由映射的服务端入口。 */
    private String defaultAccessEntry = "default";
    /** 服务端路由规则；命中不同入口时拒绝，不依赖声明顺序。 */
    private List<Route> routes = new ArrayList<>();

    @Getter
    @Setter
    public static class Account {
        /** 演示 Bearer 令牌，不含 Bearer 前缀。 */
        private String token;
        /** 已认证主体标识。 */
        private String subjectId;
        /** 服务端绑定的业务身份。 */
        private Identity identity;
    }

    @Getter
    @Setter
    public static class Identity {
        /** 引用 identities 中的身份码。 */
        private String code;
        /** 身份 ID 文本，按共享定义转换为 long、uuid 等标量。 */
        private String id;
    }

    @Getter
    @Setter
    public static class Route {
        /** Spring 路径模式，例如 /api/merchant/**。 */
        private String path;
        /** 命中路径后的可信业务入口。 */
        private String entry;
    }
}
