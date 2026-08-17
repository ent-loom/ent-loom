# ent-loom 框架文档中心

本文档中心按文档性质组织，避免同一结论在多个位置重复维护。根目录 `docs/` 记录框架级公共契约和跨模块关系；模块内部实现细节保留在对应子模块的 `docs/implementation/`。

## 文档分层与权威关系

```text
architecture  当前事实、公共契约和能力边界
standards     跨组件适用的工程与设计标准
guides        使用者和业务接入方的操作说明
decisions     为什么这样设计，以及已接受的取舍
roadmap       已确认但尚未完成的目标和优先级
work          当前实施中的计划、清单和过程记录
archive       被替代或已结束的历史材料
```

权威关系始终是：`architecture` 定义当前契约，`decisions` 解释原因，`roadmap/work` 描述变化；计划和过程文档不得覆盖当前契约。一个规则只保留一个规范性来源，其他文档通过链接引用。

## 核心规范

- 元数据裁决规范（P0）：[Metadata Resolution Contract](architecture/core/meta/metadata-resolution-contract.md)

## 当前事实

- 核心边界：[组件边界](architecture/core/component-boundaries.md)
- 治理主链：[治理 Pipeline](architecture/core/governance/pipeline.md) | [治理 Core 架构](architecture/core/governance/core-architecture.md)
- 元数据系统：[注解架构](architecture/core/meta/annotation-architecture.md) | [注解分层与适配](architecture/core/meta/layering-summary.md) | [Runtime Adapters](architecture/core/meta/runtime-adapters.md)
- CRUD 能力：[CRUD 文档入口](architecture/components/crud/index.md) | [Query](architecture/components/crud/query.md) | [Command](architecture/components/crud/command.md) | [Stats](architecture/components/crud/stats.md) | [Import](architecture/components/crud/import.md) | [Export](architecture/components/crud/export.md)
- DOC 能力：[DOC 实现说明](../ent-loom-modules/ent-loom-doc/docs/implementation.md)

## 跨能力标准

- [强类型边界与动态载荷](standards/typed-boundary.md)

## 使用指南

- CRUD：[开发指南](guides/crud/development-guide.md) | [业务集成模板](guides/crud/integration-template.md)
- 导出：[展示值渲染规范](guides/crud/export-rendering.md)
- Meta-first：[元数据驱动最佳实践](guides/meta/meta-first.md)

## 路线图

- CRUD：[CRUD 路线图](roadmap/crud/index.md)
- Meta：[Meta -> CRUD / DOC -> 业务层闭环](roadmap/meta/business-todo.md)
- Core：[Java 运行时与 Spring 兼容性版本线](roadmap/core/java-runtime-and-spring-compatibility.md)

## 当前实施

- Meta：[Meta 实施工作区](work/meta/index.md)

## 设计决策

- CRUD：[CRUD 设计决策索引](decisions/crud/index.md)
- Core：[Core 设计决策索引](decisions/core/index.md)

## 历史归档

- [2026-05-04 架构审计报告](archive/audit-report-20260504.md)
- [第 1 期已实现/未实现清单](archive/tasks/phase1-status.md)

## 模块内部实现

- CRUD：[关系查询算法](../ent-loom-modules/ent-loom-crud/docs/implementation/relation-query-logic.md) | [统计引擎细节](../ent-loom-modules/ent-loom-crud/docs/implementation/stats-engine-logic.md)
