# ent-loom 示例工程

这些示例是独立的 Spring Boot 消费者工程，用来验证框架构件在普通业务项目中的接入方式。

首个闭环是 [customer-profile-spring-boot](customer-profile-spring-boot/README.md)：显式声明一个 `CustomerProfile` 实体，通过公开 CRUD Starter 完成 MySQL 8 上的 `CREATE -> DETAIL`。日常开发连接本机 MySQL，干净验收使用独立 Compose 环境。

后续业务协作示例是 [mini-commerce-spring-boot](mini-commerce-spring-boot/README.md)：使用统一配置默认启用商品与客户 CRUD、读取可见性、实体文档和事务下单 `PlaceOrderHandler`，按场景请求展示价格快照、弱关系显式填充与业务失败验收。

示例默认消费尚未发布的 `ent-loom 2.0.0-SNAPSHOT`，运行前先按各示例 README 安装当前工作区构件。`1.0.1` 的公开消费者验收属于历史版本证据；新版本发布后需重新验证 Central 构件。
