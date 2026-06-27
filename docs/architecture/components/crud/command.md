# Command 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

Command 是 CRUD 的写入能力域，负责默认单表写、批量写、场景命令、幂等和强类型业务 Handler。

## 当前能力

| 能力 | 当前实现 |
|---|---|
| Gateway | `CommandGateway` |
| Spec | `CommandSpec` |
| 操作 | `CREATE`、`UPDATE`、`DELETE`、`SAVE_OR_UPDATE`、`ACTION` 以及显式批量 |
| 路由 | `DefaultCommandRouter` + `SceneHandlerRegistry` |
| 默认引擎 | `RegistryBackedCommandEngine` |
| 幂等 | `IdempotencyManager`，有 idempotencyKey 时包裹执行 |
| 治理 | `governCommand`，写入前完成主体、权限、数据范围和审计 |

## 语义边界

- 默认 Command 面向单表写入。
- 复杂跨表写和业务动作通过 `CommandSceneHandler` 或 `ACTION` 接管。
- 默认引擎不绕过治理和 SQL 安全。
- 强类型 handler 是推荐业务边界，动态 `CrudRecord` 保留在框架入口和低阶 SPI。

详细 Spec、routeKey 和 HTTP 合同见 [Query/Command 协议与路由明细](query-command-contract.md)。
