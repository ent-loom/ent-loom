# CRUD HTTP 契约

> 状态：Current
> 最近核验：2026-08-21
> 范围：`ent-loom-crud-spring-boot-starter`

本文是 CRUD Starter HTTP 入口、请求约束和响应结构的唯一文档。Spec、routeKey 与 Handler
语义见 [Query/Command 协议与路由](查询命令协议.md)。

## 启用方式

默认 Controller 关闭，通过以下配置启用：

```yaml
entloom:
  crud:
    controller:
      enabled: true
      base-path: /api/ent-crud
      default-timezone: Asia/Shanghai
```

Query、Command、Import、Export 还受各自的 `enabled` 开关控制。

## Query / Command / Stats

下表路径均相对于默认 base path `/api/ent-crud`，`scene` 除 `ACTION` 外均可省略。

| Domain | Method | 路径 | 请求 DTO |
|---|---|---|---|
| QUERY | `POST` | `/{entity}/page[/{scene}]` | `CrudReadHttpRequest` |
| QUERY | `POST` | `/{entity}/list[/{scene}]` | `CrudReadHttpRequest` |
| QUERY | `POST` | `/{entity}/findOne[/{scene}]` | `CrudReadHttpRequest` |
| QUERY | `POST` | `/{entity}/detail[/{scene}]` | `CrudReadHttpRequest` |
| STATS | `POST` | `/{entity}/stats[/{scene}]` | `CrudStatsHttpRequest` |
| COMMAND | `POST` | `/{entity}/create[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/update[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/delete[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/saveOrUpdate[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/createBatch[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/updateBatch[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/deleteBatch[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/saveOrUpdateBatch[/{scene}]` | `CrudCommandHttpRequest` |
| COMMAND | `POST` | `/{entity}/action/{scene}` | `CrudCommandHttpRequest` |

## Import / Export

| Domain | Method | 路径 | 结果 |
|---|---|---|---|
| IMPORT | `POST` | `/{entity}/import/validate`、`/{entity}/import/{scene}/validate` | 校验结果 |
| IMPORT | `POST` | `/{entity}/import/submit`、`/{entity}/import/{scene}/submit` | 提交结果 |
| IMPORT | `POST` | `/{entity}/import/status`、`/{entity}/import/tasks/{taskId}/status` | 任务状态 |
| IMPORT | `POST` | `/{entity}/import/error`、`/{entity}/import/tasks/{taskId}/errors/download` | 错误文件 |
| EXPORT | `POST` | `/{entity}/export/preview`、`/{entity}/export/{scene}/preview` | 预览数据 |
| EXPORT | `POST` | `/{entity}/export/submit`、`/{entity}/export/{scene}/submit` | 导出结果 |
| EXPORT | `POST` | `/{entity}/export/status`、`/{entity}/export/tasks/{taskId}/status` | 任务状态 |
| EXPORT | `POST` | `/{entity}/export/download`、`/{entity}/export/tasks/{taskId}/download` | 导出文件 |

Import 需要业务先将源文件保存到 `FileService` 并提供 `sourceFile.fileId`；Starter 不提供通用上传入口。

## 请求约束

- `scene` 只能来自路径，客户端传入 `options.scene` 会被拒绝。
- 未建模的顶层字段和 `options.*` 字段会被拒绝。
- `options.sortExpression` 不受支持。
- 服务端身份、访问入口和治理属性通过 `CrudInvocationContext` 提供，不能由普通 HTTP 参数注入。
- 下载必须在打开文件流前完成任务归属、主体、用途、过期时间和文件元数据预检。

## 响应结构

普通入口返回 `CrudResponse<T>`，稳定字段包括：

```text
success / code / message / error
requestId / traceId
operationDomain / operation / capability
data / meta
```

失败时 `error` 提供 `code`、`message`、`stage`、`routeKey`、`requestId`、`traceId`
和 `reason`。二进制下载成功时直接返回文件流；开始写出后不能再切换为 JSON 错误。

错误阶段：

| Stage | 含义 |
|---|---|
| `HTTP_CONTRACT` | HTTP DTO、未知字段或请求结构校验失败 |
| `NORMALIZE` | 请求快照或操作归一化失败 |
| `GOVERNANCE` | 主体、权限或数据范围治理失败 |
| `ROUTE` | scene 路由未命中或冲突 |
| `EXECUTE` | Handler 或默认 Engine 执行失败 |
| `UNKNOWN` | 无法归类的非框架异常 |

主要 HTTP 状态映射：

| HTTP | 错误码 |
|---:|---|
| 400 | `VALIDATION_ERROR`、`TYPE_RESOLUTION_FAILED`、`ENTITY_SCOPE_ILLEGAL`、`UNSUPPORTED_QUERY_STRATEGY`、`IDEMPOTENCY_KEY_REQUIRED` |
| 403 | `PERMISSION_DENIED`、`DATA_SCOPE_DENIED` |
| 404 | `ENTITY_NOT_EXPOSED`、`ROUTE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `ROUTE_AMBIGUOUS`、`QUERY_NOT_UNIQUE`、幂等进行中或载荷冲突 |
| 500 | 其他未显式映射错误 |

HTTP 层不直接长期暴露 `ImportResult`、`ExportResult`、`CrudTask` 或 `FileRef`；任务响应不暴露
`contextSnapshot`，文件响应不暴露存储路径、对象存储 key 或 checksum 原文。
