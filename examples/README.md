# ent-loom 示例工程

这些示例是独立的 Spring Boot 消费者工程，用来验证公开构件在普通业务项目中的接入方式。

首个闭环是 [customer-profile-spring-boot](customer-profile-spring-boot/README.md)：显式声明一个 `CustomerProfile` 实体，通过公开 CRUD Starter 完成 MySQL 8 上的 `CREATE -> DETAIL`。日常开发连接本机 MySQL，干净验收使用独立 Compose 环境。

后续业务协作示例是 [mini-commerce-spring-boot](mini-commerce-spring-boot/README.md)：商品和客户使用通用 CRUD，下单通过事务 `PlaceOrderHandler` 完成价格快照、订单明细和业务失败验收。

示例默认消费 Maven Central 的 `ent-loom 1.0.1`，当前均为正式交付。单表示例已通过全新 Maven 仓库中的公开构件下载、启动、HTTP 和 SQL 验收；商城随后使用同一份仅含公开下载构件的仓库，通过真实业务路径、价格快照和事务回滚验收。2026-09-09，GitHub Actions 运行 `34333670911` 再次通过公开消费者、单表、商城及关联框架门禁。验证框架源码改动时，使用各示例 README 中的当前工作区构件路径；该路径不替代公开版本验收。
