# ent-loom

`ent-loom` 是一个元数据驱动的业务开发框架，旨在通过声明式的方式快速构建具备治理能力、高性能和高度可扩展的后端服务。

## 🌟 核心特性

- **元数据驱动 (Meta-first)**: 业务语义一次定义，多处复用（CRUD、文档、校验、UI 等）。
- **治理主链**: 统一的主体解析、权限判定、数据范围限制和全链路审计。
- **关系查询引擎**: 自动化处理实体间的关联关系，支持根优先的关系展开。
- **高性能聚合**: 内置 Stats 引擎，支持单表及复杂聚合统计。
- **高度模块化**: 核心抽象与实现解耦，支持 JDBC、Excel、Web 等多种能力平滑接入。

## 📂 项目结构

- `ent-loom-meta`: 核心元数据定义与解析引擎。
- `ent-loom-components`: 核心能力组件库。
- `ent-loom-crud`: CRUD 核心套件（API, Core, Engine, Starter）。
- `ent-loom-ddl`: 数据库 schema 管理与迁移工具。
- `ent-loom-doc`: 自动化文档生成工具。
- `ent-loom-ui`: UI 元数据投影与基础组件。

## 📚 文档中心

所有的详细文档、设计决策和接入指南都存放在 [docs/](./docs/index.md) 目录下：

- [架构真相](./docs/architecture/components/crud/overview.md): 系统当前是如何工作的。
- [接入指南](./docs/guides/meta/meta-first.md): 开发者如何快速上手。
- [决策记录](./docs/decisions/crud/index.md): 关键技术决策背后的逻辑。
- [路线图](./docs/roadmap/meta/business-todo.md): 正在进行和计划中的工作。

## 🛠️ 快速开始

请参考 [业务集成模板](./docs/guides/crud/integration-template.md) 了解如何将 `ent-loom` 引入您的项目。

---
© 2026 ent-loom Team.
