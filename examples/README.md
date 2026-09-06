# ent-loom 示例工程

这些示例是独立的 Spring Boot 消费者工程，用来验证公开构件在普通业务项目中的接入方式。

首个闭环是 [customer-profile-spring-boot](customer-profile-spring-boot/README.md)：显式声明一个 `CustomerProfile` 实体，通过公开 CRUD Starter 完成 MySQL 8 上的 `CREATE -> DETAIL`。日常开发连接本机 MySQL，干净验收使用独立 Compose 环境。

示例默认依赖 Maven Central 上已发布的 `ent-loom` `1.0.0`。框架源码构建验证需要先将当前工作区安装到隔离的本地 Maven 仓库，再执行示例目录中的验证脚本。
