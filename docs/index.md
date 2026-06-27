# ent-loom 框架文档中心

本文档中心按“当前事实、使用指南、路线图、设计决策、历史归档”组织。根目录 `docs/` 记录框架级公共契约和跨模块关系；模块内部实现细节保留在对应子模块的 `docs/implementation/`。

## 当前事实

- 核心边界：[组件边界](architecture/core/component-boundaries.md)
- 治理主链：[治理 Pipeline](architecture/core/governance/pipeline.md) | [治理 Core 架构](architecture/core/governance/core-architecture.md)
- 元数据系统：[注解架构](architecture/core/meta/annotation-architecture.md) | [注解分层与适配](architecture/core/meta/layering-summary.md) | [Runtime Adapters](architecture/core/meta/runtime-adapters.md)
- CRUD 能力：[CRUD 文档入口](architecture/components/crud/index.md) | [Query](architecture/components/crud/query.md) | [Command](architecture/components/crud/command.md) | [Stats](architecture/components/crud/stats.md) | [Import](architecture/components/crud/import.md) | [Export](architecture/components/crud/export.md)
- DOC 能力：[DOC 实现说明](../ent-loom-components/ent-loom-doc/docs/implementation.md)

## 使用指南

- CRUD：[开发指南](guides/crud/development-guide.md) | [业务集成模板](guides/crud/integration-template.md)
- 导出：[展示值渲染规范](guides/crud/export-rendering.md)
- Meta-first：[元数据驱动最佳实践](guides/meta/meta-first.md)

## 路线图

- CRUD：[CRUD 路线图](roadmap/crud/index.md)
- Meta：[Meta -> CRUD / DOC -> 业务层闭环](roadmap/meta/business-todo.md)

## 设计决策

- CRUD：[CRUD 设计决策索引](decisions/crud/index.md)
- Core：[SceneValidator 场景校验设计](decisions/core/scene-validation-design.md)

## 历史归档

- [2026-05-04 架构审计报告](archive/audit-report-20260504.md)
- [第 1 期已实现/未实现清单](archive/tasks/phase1-status.md)

## 模块内部实现

- CRUD：[关系查询算法](../ent-loom-components/ent-loom-crud/docs/implementation/relation-query-logic.md) | [统计引擎细节](../ent-loom-components/ent-loom-crud/docs/implementation/stats-engine-logic.md)
