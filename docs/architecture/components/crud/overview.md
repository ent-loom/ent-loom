# CRUD 核心组件架构

`ent-loom-crud` 是框架的核心能力组件，提供了一套高度一致、安全且可扩展的增删改查与统计分析能力。它不仅是简单的数据库访问层，更是一个集成了治理、路由和动态编排的业务中枢。

## 1. 核心理念：统一执行模型

无论业务请求是简单的单表查询，还是复杂的跨表聚合，在 `ent-loom-crud` 中都遵循同一条执行主链：

`请求 DTO -> 规格化 (Spec) -> 治理 (Governance) -> 路由 (Router) -> 执行引擎 (Engine) -> 审计/响应`

### 1.1 治理不可绕过
所有的 CRUD 操作必须经过治理主链（7 阶段），确保每一行数据的读写都经过了身份验证、权限判定和数据范围（Data Scope）的限制。

### 1.2 动态性与强类型的平衡
- **动态性**: 使用 `CrudRecord`（基于 Map）承载数据，支持在不重新发布代码的情况下动态增减字段。
- **强类型**: 内部使用 `Spec` 对象定义操作协议，支持通过 `SceneHandler` 注入强类型的业务逻辑。

## 2. 核心五大能力

框架将 CRUD 拆分为五个独立的一等公民能力（Capability）：

| 能力 (Capability) | 核心职责 | 合法操作 (Operation) |
|---|---|---|
| **QUERY** | 只读数据查询 | `PAGE / LIST / DETAIL / FIND_ONE` |
| **COMMAND** | 数据写入与操作 | `CREATE / UPDATE / DELETE / ACTION` |
| **STATS** | 聚合统计与指标计算 | `QUERY / PREVIEW` |
| **IMPORT** | 异步/批量数据导入 | `VALIDATE / SUBMIT / COMMIT / STATUS` |
| **EXPORT** | 数据生成与下载 | `SUBMIT / DOWNLOAD / STATUS / PREVIEW` |

## 3. 分层架构

### 3.1 API 层 (ent-loom-crud-api)
定义了对外稳定的 DTO、过滤操作符（EQ, LIKE, IN 等）、排序规则和错误码。它是业务系统与框架通信的语言。

### 3.2 核心层 (ent-loom-crud-core)
- **Gateway**: 每一级能力的入口（QueryGateway, CommandGateway 等）。
- **Runtime**: 负责执行上下文、路由选择和场景注册。
- **Foundation**: 定义了读写抽象，如 `QuerySpec` 和 `CommandSpec`。

### 3.3 引擎层 (ent-loom-crud-engine-jdbc)
基于元数据的默认 JDBC 实现。它负责将 `Spec` 翻译为安全的 SQL 并执行。支持 `ROOT_FIRST` 关系补数策略，避免了复杂的 JOIN 关联。

### 3.4 适配层 (ent-loom-meta-adapter-crud)
将通用元数据（EntMeta）投影到 CRUD 运行时模型（EntityMeta），实现“一次定义，多处复用”。

## 4. 路由机制

框架通过 `(EntityType, Operation, Scene)` 三元组定位执行逻辑：
- **默认路径**: 如果没有匹配的 `SceneHandler`，则回落到默认的 JDBC 引擎。
- **场景路径**: 业务通过实现 `QuerySceneHandler` 或 `CommandSceneHandler` 来接管特定场景的逻辑。

## 5. 跨表查询策略

目前默认支持 **`ROOT_FIRST` (根优先)** 策略：
1. **根查询**: 首先根据过滤条件查询主实体数据。
2. **关系补数**: 根据元数据中定义的 `RelationEdge`，通过 `IN` 子查询批量获取关联实体的数据。
3. **内存装配**: 在 Java 层完成对象树的组装，支持 1:1、1:N 关系的递归加载。

这种策略在多租户、大数据量环境下比 JOIN 具备更好的性能表现和隔离性。
