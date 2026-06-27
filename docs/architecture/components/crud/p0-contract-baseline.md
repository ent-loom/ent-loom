# CRUD P0 Contract Baseline

本文记录 P0 干净重构后的外部运行契约和内部迁移替代表。它用于评审破坏式内部重构是否误伤 HTTP / JSON / 配置入口。

## HTTP 路由

默认 base path：`/api/ent-crud`。

| Capability | Method | Route | Operation |
| --- | --- | --- | --- |
| QUERY | `POST` | `/{entity}/page/{scene}` | `QUERY/PAGE` |
| QUERY | `POST` | `/{entity}/list/{scene}` | `QUERY/LIST` |
| QUERY | `POST` | `/{entity}/detail/{scene}` | `QUERY/DETAIL` |
| QUERY | `POST` | `/{entity}/find-one/{scene}` | `QUERY/FIND_ONE` |
| COMMAND | `POST` | `/{entity}/create/{scene}` | `COMMAND/CREATE` |
| COMMAND | `POST` | `/{entity}/update/{scene}` | `COMMAND/UPDATE` |
| COMMAND | `POST` | `/{entity}/delete/{scene}` | `COMMAND/DELETE` |
| COMMAND | `POST` | `/{entity}/save-or-update/{scene}` | `COMMAND/SAVE_OR_UPDATE` |
| COMMAND | `POST` | `/{entity}/create-batch/{scene}` | `COMMAND/CREATE_BATCH` |
| COMMAND | `POST` | `/{entity}/update-batch/{scene}` | `COMMAND/UPDATE_BATCH` |
| COMMAND | `POST` | `/{entity}/delete-batch/{scene}` | `COMMAND/DELETE_BATCH` |
| COMMAND | `POST` | `/{entity}/save-or-update-batch/{scene}` | `COMMAND/SAVE_OR_UPDATE_BATCH` |
| COMMAND | `POST` | `/{entity}/action/{scene}` | `COMMAND/ACTION` |
| STATS | `POST` | `/{entity}/stats/{scene}` | `STATS/QUERY` |
| EXPORT | `POST` | `/{entity}/export/preview/{scene}` | `EXPORT/PREVIEW` |
| EXPORT | `POST` | `/{entity}/export/submit/{scene}` | `EXPORT/SUBMIT` |
| IMPORT | `POST` | `/{entity}/import/validate/{scene}` | `IMPORT/VALIDATE` |
| IMPORT | `POST` | `/{entity}/import/submit/{scene}` | `IMPORT/SUBMIT` |

`scene` 是稳定业务语义。空 scene 只允许走默认引擎；非空 scene 未命中 handler 时必须 fail-fast。

## JSON 样例

Query request：

```json
{
  "entityCodes": ["order", "order_item"],
  "options": {
    "page": 1,
    "limit": 20,
    "filter": {
      "status": "ACTIVE"
    },
    "sorts": [
      {"field": "id", "direction": "DESC"}
    ]
  }
}
```

Command request：

```json
{
  "id": 1001,
  "values": {
    "orderNo": "ORD-1001"
  }
}
```

Stats request：

```json
{
  "metrics": [
    {"alias": "orderCount", "function": "COUNT", "field": "id"}
  ],
  "groups": [
    {"field": "status", "alias": "status"}
  ]
}
```

稳定响应字段：

| Response | Stable fields | Ignored in snapshot |
| --- | --- | --- |
| success | `success`、`code`、`operationDomain`、`operation`、`data`、`page` | `requestId`、`traceId`、时间戳 |
| failure | `success=false`、`code`、`operationDomain`、`operation`、`error.stage`、`error.reason` | `requestId`、`traceId`、detail 顺序 |

## Error Matrix

| Code | Stage | Reason | HTTP |
| --- | --- | --- | --- |
| `VALIDATION_ERROR` | `VALIDATION` | 请求字段、主键策略、Spec 结构非法 | 400 |
| `ENTITY_SCOPE_ILLEGAL` | `VALIDATION` | `entityCodes` 与路由实体或关系图不匹配 | 400 |
| `SCENE_HANDLER_NOT_FOUND` | `ROUTING` | 非空 scene 未命中 handler | 400 |
| `UNSUPPORTED_OPERATION` | `ENGINE` | 默认引擎未声明支持该 capability/operation | 400 |
| `QUERY_NOT_UNIQUE` | `EXECUTION` | `FIND_ONE` / `DETAIL` 命中多条 | 409 |
| `NOT_FOUND` | `EXECUTION` | `DETAIL` 或命令目标不存在 | 404 |
| `GOVERNANCE_DENIED` | `GOVERNANCE` | 权限、数据范围或主体校验拒绝 | 403 |

## 配置 Key

| Key | Default | Contract |
| --- | --- | --- |
| `entloom.crud.controller.enabled` | `false` | 是否启用默认 HTTP controller |
| `entloom.crud.controller.base-path` | `/api/ent-crud` | 默认 HTTP base path |
| `entloom.crud.controller.default-timezone` | `Asia/Shanghai` | time 参数默认时区 |
| `entloom.crud.query.enabled` | module default | Query 自动装配开关 |
| `entloom.crud.command.enabled` | module default | Command 自动装配开关 |
| `entloom.crud.import-export.enabled` | module default | Import / Export 自动装配开关 |
| `entloom.crud.governance.permission-rules` | empty | 配置型权限规则 |
| `entloom.crud.governance.audit.persist-to-jdbc` | `false` | 是否写 JDBC 审计表 |
| `ent.loom.meta.base-packages` | empty | Meta adapter 实体扫描包；配置后未扫描到实体会启动失败 |
| `ent.loom.meta.entity-class-names` | empty | Meta adapter 实体类清单 |
| `ent.loom.meta.crud.enabled` | `true` | 是否启用 Meta -> CRUD adapter |

## Java 迁移替代表

| Old | New | Strategy |
| --- | --- | --- |
| `ReflectiveEntityMetaRegistry` | `CrudNativeRuntimeModelParser -> CrudRuntimeModelBackedEntityMetaRegistry` | 旧反射 registry 已删除；native 注解只作为 model parser 输入 |
| `AdapterBackedEntityMetaRegistry` | `CrudRuntimeModelBackedEntityMetaRegistry` | 已删除旧 adapter-backed registry |
| `ResourceCatalogAdapter.entityMetas()/relationEdges()` | `ResourceCatalogAdapter.runtimeModel()` | 旧方法已删除；adapter 只能输出统一 runtime model |
| 全局 operation string | `CrudOperationKey(domain, operation)` | 由 `CrudOperationMatrix` 校验 |
| `QueryOperation.STATS` | `StatsOperation.QUERY` / `StatsOperation.PREVIEW` | Stats 是独立 capability |

运行期硬约束：Spring 默认 `EntityMetaRegistry` 必须由 `ResourceCatalogAdapter.runtimeModel()` 构建。没有 adapter 时启动失败。
