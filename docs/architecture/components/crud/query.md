# Query 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

Query 是 CRUD 的只读能力域，负责单表读取、受控关系展开、场景查询和默认 JDBC 查询。

## 当前能力

| 能力 | 当前实现 |
|---|---|
| Gateway | `QueryGateway` |
| Spec | `QuerySpec` |
| 操作 | `PAGE`、`LIST`、`FIND_ONE`、`DETAIL` |
| 路由 | `DefaultQueryRouter` + `SceneHandlerRegistry` |
| 默认引擎 | `JdbcQueryEngine` |
| 关系策略 | 默认 `ROOT_FIRST`，有限关系展开 |
| 治理 | `governQuery`，读取前完成主体、权限、数据范围和审计 |

## 语义边界

- `detail` 表示必须存在，不存在抛稳定错误。
- `findOne` 表示允许不存在，但不允许结果不唯一。
- 默认 Query 不是通用 JOIN 生成器。
- 关联字段过滤、排序和复杂多跳规划不属于当前默认能力。

详细 Spec、routeKey 和 HTTP 合同见 [Query/Command 协议与路由明细](query-command-contract.md)。
