# Meta 路线图

> 状态：Remaining
> 上游契约：[元数据约定与裁决契约](../../../architecture/core/元数据约定与裁决契约.md)

Meta 的完整文档导航见 [Meta 领域总览](../../../domains/meta/index.md)。

Meta -> CRUD / DOC 的静态 Adapter 已形成当前闭环。这里仅跟踪尚未完成的跨模块裁决能力，不重复当前架构和历史实施过程。

## 当前主线

[元数据裁决实施计划](元数据裁决实施计划.md) 当前推进公共 Contribution、属性级 Resolver 和来源诊断，然后依次接入 Meta Convention 与 CRUD 消费闭环。

## 后续目标

| 优先级 | 目标 | 启动条件 |
|---|---|---|
| P0 | 公共 Contribution 与属性级 Resolver | 当前实施主线 |
| P0 | Meta / CRUD Project Convention | Resolver 契约稳定 |
| P1 | Maven 边界守卫与来源闭环测试 | 核心消费者闭环完成 |
| P1 | DDL Adapter | DDL Runtime Model 边界稳定 |
| P1 | DOC Resolver 收敛 | 公共 Resolver 已有两个真实消费者 |
| P2 | UI Adapter | UI Runtime Model 和 API 稳定 |
| 后续 | 包扫描式实体发现 | 显式 class list 成为明确瓶颈 |
| 远期 | OpenAPI 投影、动态刷新 | 核心静态模型稳定后单独决策 |

## 已完成且不再跟踪

- Meta Annotation -> Descriptor。
- Meta -> CRUD / DOC 静态 Adapter。
- Native-only、Meta-only、Meta + Module 基础路径。
- CRUD 唯一 Runtime Model 与冻结 Registry。

## 维护规则

- 当前事实维护在 `docs/architecture/core/meta/`。
- 本页只保存长期剩余目标。
- 当前阶段任务只在实施计划维护。
- 已完成阶段从路线图删除，必要的设计理由提炼为 Decision。
