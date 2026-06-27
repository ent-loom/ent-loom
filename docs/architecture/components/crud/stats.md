# Stats 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

Stats 是 CRUD 的统计能力域，和 Query、Command、Import、Export 平级。它不是 Query 的附属模式，而是独立 operation domain。

## 当前能力

| 能力 | 当前实现 |
|---|---|
| Gateway | `StatsGateway` |
| Spec | `StatsSpec` |
| 默认引擎 | `StatsQueryEngine` + `JdbcStatsQueryExecutor` |
| 能力范围 | 单表指标、分组、having、分页/list/scalar |
| SQL 阶段 | 通过 SQL guard 和字段白名单执行 |
| 治理 | `governStats`，统计前完成主体、权限、数据范围和审计 |

## 语义边界

- Stats 当前面向单表聚合。
- 复杂多表指标、跨服务指标和指标编排不属于当前默认能力。
- Stats 有独立 operation key，不应伪装成普通 Query。

内部算法细节见 [单表统计引擎架构](../../../../ent-loom-components/ent-loom-crud/docs/implementation/stats-engine-logic.md)。
