# CRUD 文档入口

本目录记录 `ent-loom` 整体框架视角下的 CRUD 当前事实、公共契约和能力边界。

## 阅读顺序

1. [CRUD 核心组件架构](overview.md)
2. [总体运行时架构](runtime-architecture.md)
3. [Query 当前实现](query.md)
4. [Command 当前实现](command.md)
5. [Stats 当前实现](stats.md)
6. [Import 当前实现](import.md)
7. [Export 当前实现](export.md)
8. [Task / File 当前实现](task-file.md)
9. [HTTP Contract 当前实现](http-contract.md)
10. [Query/Command 协议与路由明细](query-command-contract.md)
11. [Default Engine 当前实现](default-engine.md)
12. [Scene Policy 治理边界定稿](scene-policy-governance.md)
13. [稳定规则清单](stable-rules.md)

## 文档边界

- 本目录只放 CRUD 的当前能力、公共契约和框架级说明。
- CRUD 内部算法、包结构和维护细节放在 `ent-loom-components/ent-loom-crud/docs/implementation/`。
- 尚未完全落地的增强项按能力域放在 `docs/roadmap/crud/`。
- 破坏式重构建议、命令包调整等历史取舍放在 `docs/decisions/crud/`。
