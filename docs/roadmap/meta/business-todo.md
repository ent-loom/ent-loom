# Meta 后续路线

> Status: Remaining
> Verified: 2026-05-04
> Scope: `ent-loom-meta`、`ent-loom-meta-adapters`

Meta -> CRUD / DOC 的启动期静态闭环已经实现。当前事实见 [Meta Runtime Adapters 当前实现](../../architecture/core/meta/runtime-adapters.md)。历史推进清单全文见 [Meta -> CRUD / DOC -> 业务层闭环待实现清单](../../archive/meta/meta-business-todo-plan.md)。

## 已实现，不再作为 TODO 跟踪

- Meta annotation parser 输出 Descriptor。
- Meta -> CRUD 静态 adapter 输出 `EntityMeta` / `RelationEdge`。
- Meta -> DOC 静态 adapter 输出 `DocEntityModel` 和兼容文档 map。
- starter 条件装配 `MetaCrudAdapter` / `MetaDocAdapter`。
- Meta-only、CRUD-only、DOC-only、disabled、empty class list 路径独立运行。
- 业务 `ResourceCatalogAdapter` 与 Meta adapter 并存。
- `DocOverrideProvider` 接入 DOC 输出覆盖。
- registry 冲突边界和 adapter 诊断有测试覆盖。

## 剩余路线

| 优先级 | 工作 | 当前原因 |
|---|---|---|
| 后续增强 | DDL adapter 接入 | 当前闭环只覆盖 CRUD / DOC |
| 后续增强 | UI schema adapter 接入 | UI 目前有独立 core，尚未纳入 Meta starter 闭环 |
| 后续增强 | 包扫描式实体发现 | 当前 starter 以显式 class names 为主 |
| 远期增强 | API 文档 / OpenAPI 投影 | 当前 DOC 输出是实体文档模型，不是 API 契约生成 |
| 远期增强 | 运行期动态刷新 | 当前是启动期静态解析与装配 |

## 维护规则

- 当前实现事实维护在 `docs/architecture/core/meta/`。
- 业务接入实践维护在 `docs/guides/meta/`。
- 本文只保留尚未完成的后续路线。
