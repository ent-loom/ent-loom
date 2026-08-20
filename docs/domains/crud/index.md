# CRUD 领域总览

> 状态：Current

本页是 CRUD 的统一导航入口，只负责串联当前架构、使用指南、设计决策和演进路线，不复制各类文档正文。

## 阅读路径

1. 先看 [CRUD 架构总览](../../architecture/components/crud/overview.md)，了解操作域、运行时模型和执行边界。
2. 再看 [CRUD 架构文档入口](../../architecture/components/crud/index.md)，按合同、能力域和默认实现查阅当前事实。
3. 需要接入业务时，进入 [CRUD 使用指南](../../guides/crud/index.md)。
4. 需要了解取舍或后续工作时，进入 [CRUD 设计决策](../../evolution/decisions/crud/index.md) 或 [CRUD 路线图](../../evolution/roadmap/crud/index.md)。

## 文档分层

| 目的 | 入口 | 内容边界 |
|---|---|---|
| 当前架构 | [CRUD 架构文档入口](../../architecture/components/crud/index.md) | 当前合同、Runtime Model、执行链和默认实现 |
| 业务接入 | [CRUD 使用指南](../../guides/crud/index.md) | 接入步骤、开发方式和配置示例 |
| 设计取舍 | [CRUD 设计决策](../../evolution/decisions/crud/index.md) | 已接受的关键原则、背景和后果 |
| 后续工作 | [CRUD 路线图](../../evolution/roadmap/crud/index.md) | 尚未完成的目标、阶段和验收条件 |

## 当前能力

- Query、Command、Stats
- Import、Export、Task / File
- 治理 Pipeline、路由、默认 Engine 和 Runtime Registry
- 强类型 Handler 边界与业务 Adapter SPI

当前能力以 `architecture` 下的文档为准；指南、决策和路线图不能覆盖当前架构事实。
