# 总体架构总览

`ent-loom-crud` 的当前实现是一条“统一网关 + 治理主链 + 可截获路由 + 默认 JDBC 引擎”的 CRUD 主链。框架把普通单表 CRUD、ROOT_FIRST 关系读、Stats 聚合、业务场景 Handler、权限治理、SQL 安全和 Spring Boot HTTP 接入收敛到同一套 Spec 与 routeKey 上。

## 分层结构

```mermaid
flowchart TB
    http["HTTP Controller<br/>EntCrudQuery/Command/StatsController"]
    facade["HTTP Facade<br/>EntCrudQuery/Command/StatsFacade"]
    assembler["Spec Assembler<br/>CrudQuery/Command/StatsSpecAssembler"]
    gateway["Gateway<br/>QueryGateway / CommandGateway / StatsGateway"]
    governance["Governance<br/>DefaultCrudGovernanceService"]
    router["Router + Scene Registry<br/>DefaultQueryRouter / DefaultCommandRouter"]
    statsEngine["StatsQueryEngine"]
    queryEngine["QueryEngine<br/>JdbcQueryEngine"]
    commandEngine["CommandEngine<br/>RegistryBackedCommandEngine"]
    jdbc["GuardedSqlExecutor<br/>SQL Guard + JdbcTemplate + SQL Log"]
    meta["EntityMetaRegistry<br/>Reflective or business bridge"]

    http --> facade --> assembler --> gateway
    gateway --> governance
    governance --> router
    governance --> statsEngine
    router --> queryEngine
    router --> commandEngine
    statsEngine --> jdbc
    queryEngine --> jdbc
    commandEngine --> jdbc
    queryEngine --> meta
    commandEngine --> meta
    statsEngine --> meta
    governance --> meta
```

## Spring 装配入口

`CrudAutoConfiguration` 导入 `CrudCoreConfiguration` 和 `CrudWebAutoConfiguration`。核心配置再按依赖顺序导入公共能力、幂等、安全、Stats、Query、Command 和 Gateway。

```mermaid
flowchart LR
    auto["CrudAutoConfiguration"]
    core["CrudCoreConfiguration"]
    web["CrudWebAutoConfiguration"]
    common["CrudCommonConfiguration"]
    idempotency["CrudIdempotencyConfiguration"]
    sql["CrudSqlSecurityConfiguration"]
    stats["CrudStatsEngineConfiguration"]
    query["CrudQueryEngineConfiguration"]
    command["CrudCommandEngineConfiguration"]
    gateway["CrudGatewayConfiguration"]

    auto --> core
    auto --> web
    core --> common
    core --> idempotency
    core --> sql
    core --> stats
    core --> query
    core --> command
    core --> gateway
```

关键 Bean：

| Bean | 默认实现 | 可替换点 |
|---|---|---|
| `EntityMetaRegistry` | `CrudRuntimeModelBackedEntityMetaRegistry` | 业务提供 `ResourceCatalogAdapter` / `CrudRuntimeModel` |
| `CrudSubjectResolver` | `FailClosedCrudSubjectResolver` | 业务必须替换为登录态解析 |
| `CrudPermissionService` | `RuleBasedCrudPermissionService` | 业务可替换为角色/策略实现 |
| `CrudDataScopeResolver` | `DefaultCrudDataScopeResolver` | 业务可替换为组织/学校/班级/学生范围 |
| `CrudGovernanceAuditRecorder` | logging + optional JDBC | 业务可替换审计落库/日志 |
| `QueryEngine` | `JdbcQueryEngine` | 可替换查询引擎 |
| `CommandEngine` | `RegistryBackedCommandEngine` | 可替换命令引擎 |
| `StatsQueryExecutor` | `JdbcStatsQueryExecutor` | 可替换 Stats 执行器 |

## 一次 Query 调用

```mermaid
sequenceDiagram
    participant C as Controller/Facade
    participant A as CrudQuerySpecAssembler
    participant G as QueryGateway
    participant Gov as GovernanceService
    participant R as QueryRouter
    participant H as SceneHandler or Default Handler
    participant E as JdbcQueryEngine
    participant SQL as GuardedSqlExecutor

    C->>A: HTTP DTO -> QuerySpec
    A-->>C: immutable QuerySpec
    C->>G: page/list/findOne/detail(spec)
    G->>Gov: governQuery(spec)
    Gov-->>G: QueryExecutionSpec with subject/scope/decision
    G->>R: route(governedSpec)
    R-->>G: QueryRoute(handler, defaultStrategy)
    G->>H: handler.operation(executionSpec)
    alt custom SceneHandler registered
        H->>E: delegate.invoke(modifiedSpec)
    else default path
        H->>E: defaultQueryEngine.operation(spec)
    end
    E->>SQL: compiled SQL
    SQL-->>E: rows/count
    E-->>G: result
    G->>Gov: recordAllow or recordExecutionFailure
```

## 一次 Command 调用

```mermaid
sequenceDiagram
    participant C as Controller/Facade
    participant A as CrudCommandSpecAssembler
    participant G as CommandGateway
    participant Gov as GovernanceService
    participant IDEM as IdempotencyManager
    participant R as CommandRouter
    participant H as SceneHandler or Default Handler
    participant E as RegistryBackedCommandEngine
    participant SQL as GuardedSqlExecutor

    C->>A: HTTP DTO -> CommandSpec
    A-->>C: immutable CommandSpec
    C->>G: action(spec)
    G->>Gov: governCommand(spec)
    Gov-->>G: CommandExecutionSpec
    G->>R: route(executionSpec)
    alt idempotencyKey present and not dryRun
        G->>IDEM: executeWithIdempotency(storageKey, payload)
        IDEM->>H: invoke route handler
    else no idempotency
        G->>H: invoke route handler
    end
    alt custom SceneHandler registered
        H->>E: delegate.invoke(modifiedSpec)
    else default single/batch write
        H->>E: defaultCommandEngine.action(spec)
    end
    E->>SQL: insert/update/delete SQL
    SQL-->>E: rows/generatedKey
    G->>Gov: recordAllow or recordExecutionFailure
```

## 当前能力边界

| 能力 | 当前实现 |
|---|---|
| 单表 Query | `PAGE/LIST/FIND_ONE/DETAIL` 已实现 |
| 单表 Command | `CREATE/UPDATE/DELETE/SAVE_OR_UPDATE` 和显式批量已实现，`ACTION` 必须定制 |
| 关系 Query | `ROOT_FIRST` 一跳/有限路径展开，默认不做 join 过滤/排序 |
| Stats | 单表指标、分组、having、分页/list/scalar 已实现 |
| 治理 | 主体解析、权限判定、数据范围交集、审计闭环已实现 |
| SQL 安全 | 字段白名单、参数规模限制、执行前占位符检查、SQL 日志已实现 |
| HTTP | 默认 Starter Controller 可开关；业务也可复用 Facade 自建 Controller |
