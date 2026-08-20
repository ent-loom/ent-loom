# CRUD 架构文档入口

> 状态：Current
> 最近核验：2026-08-21

本目录描述 CRUD 的当前合同和实现。完整的 CRUD 领域导航见 [CRUD 领域总览](../../../domains/crud/index.md)。阅读时先看总览，再按问题进入具体文档。

## 总览

1. [CRUD 核心组件](overview.md)
2. [运行时架构](runtime-architecture.md)

## 公开合同

- [HTTP Contract](http-contract.md)
- [Query/Command 协议与路由](query-command-contract.md)
- [强类型边界](typed-boundary.md)

## 能力域

- [Query](query.md)
- [Command](command.md)
- [Stats](stats.md)
- [Import](import.md)
- [Export](export.md)
- [Task / File](task-file.md)

## 默认实现

- [Default Engine](default-engine.md)
- [Runtime Registry](runtime-registry.md)
- [关系查询实现](../../../../ent-loom-modules/ent-loom-crud/docs/implementation/relation-query-logic.md)
- [Stats 引擎实现](../../../../ent-loom-modules/ent-loom-crud/docs/implementation/stats-engine-logic.md)

## 演进

- [CRUD 设计决策](../../../evolution/decisions/crud/index.md)
- [CRUD 路线图](../../../evolution/roadmap/crud/index.md)

本目录不保存实施步骤或历史方案，未完成目标统一进入 Roadmap。
