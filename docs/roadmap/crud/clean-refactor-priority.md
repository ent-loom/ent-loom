# CRUD 干净重构优先级

本文按“不保留 Java 包名 / Maven artifact / 旧 SPI 兼容层”的口径，整理当前 `ent-loom-crud` 后续开发前必须优先处理的大问题。目标是先把运行契约、元数据入口和模块边界收敛，再继续扩展 Import / Export、Relation Query 等能力。

本文不是要求一次性完成所有重构，而是作为拆分小闭环的优先级依据。每个闭环都应保持可编译、可测试、可评审，并且只解决一个主问题：恢复测试、冻结契约、定义模型、冻结 registry、能力矩阵治理或模块边界收口。

## 重构原则

- 运行期只消费已冻结的元数据快照，不在请求链路中注册实体、补关系或修补字段定义。
- 外部 HTTP / JSON / 配置契约可以按业务需要保留；内部 Java 包名、SPI、默认实现位置不保兼容。
- 大规模搬模块、改包名、删 artifact 前，先冻结外部运行契约和一次性迁移替代表。
- 先修硬失败和主链不确定性，再拆模块、补架构守卫。
- 对请求态参数保持运行期动态，例如 `entityCodes`、`filters`、`sorts`、`scene`；对实体定义、关系定义、字段能力保持启动期确定。
- `capability / operation / scene` 必须作为治理、审计、幂等和默认引擎选择的稳定维度；不能把 operation 当成全局无约束枚举。
- Import / Export 先定义 core 抽象边界，Excel、文件存储、异步任务执行器等重实现不得反向污染 core。
- 每个重构闭环只做一类高风险变更，避免在同一 PR 中同时改运行语义、搬模块、改包名和删兼容入口。
- 破坏内部 Java 包名、Maven artifact 或 SPI 前，必须先列出影响面和迁移替代表，并确认本仓所有引用能在同一闭环内迁移完成。

## 小闭环执行规则

每个执行切片都应包含：

- 改动范围：明确涉及哪些 module、包、入口和测试。
- 前置条件：是否依赖契约基线、全仓测试恢复、模型定义或迁移替代表。
- 验收命令：至少包含当前 module 测试；影响 starter、core 或注解时应包含全仓编译或全仓测试。
- 禁止事项：本切片不顺手搬模块、不顺手改包名、不顺手删除旧入口，除非这正是该切片的目标。
- 兼容策略：说明本切片是一次性全仓替换，还是允许短期 bridge / adapter，并标明删除时间点。

## P0：先修主链正确性，小闭环推进

当前状态：

- P0.1 JDBC 默认写入链已通过 `EntityIdPolicy` 修复，并覆盖显式主键、生成主键、批量 save-or-update 和非法主键输入。
- P0.2 已收敛到 `CrudRuntimeModel`，Meta-first / CRUD-only / Meta + CRUD 均通过 `ResourceCatalogAdapter.runtimeModel()` 进入统一模型。
- P0.3 运行期 registry 已切到 `CrudRuntimeModelBackedEntityMetaRegistry`，关系图启动期预计算，`RelationGraph` / `RelationEdge` 运行期只读。
- 旧 `ReflectiveEntityMetaRegistry` 与 `AdapterBackedEntityMetaRegistry` 已删除；CRUD native 注解只通过 `CrudNativeRuntimeModelParser` 输出 `CrudRuntimeModel`。
- P0.4 capability / operation 矩阵由 `CrudOperationMatrix` 校验，Stats 已独立为 `STATS/QUERY` 与 `STATS/PREVIEW`。
- 外部契约和迁移替代表见 `docs/architecture/components/crud/p0-contract-baseline.md`。

### 0. 冻结外部运行契约和迁移替代表

本轮允许不保留旧 Java 包名、Maven artifact 和旧 SPI 兼容层，但不能在重构过程中误伤已上线外部运行契约。模块收敛和删除旧目录前，应先把当前契约沉淀为可评审、可回归的资产。

建议拆成三个小闭环：

1. 契约清单闭环：只补 HTTP / JSON / 错误码 / 配置 key 清单，不改生产代码。
2. 契约测试闭环：把关键样例转为契约测试或 snapshot 测试，明确动态字段忽略规则。
3. 迁移替代表闭环：盘点旧包名、旧类型、旧 artifact 和旧 SPI，并给出新位置和迁移方式。

建议补齐：

- HTTP 路由清单：URL、method、path variable、query 参数、请求体样例。
- JSON snapshot：成功响应、失败响应、分页、详情、命令、统计的典型样例。
- 错误码矩阵：code、stage、reason、HTTP status、触发条件。
- 配置 key 清单：旧 key、新 key、默认值、兼容策略。
- Java 迁移替代表：旧类型、旧包名、旧 artifact、新类型、新包名、新模块。

snapshot 测试要求：

- 明确稳定字段和忽略字段，例如时间、traceId、请求 id、排序不稳定的集合。
- 成功响应和失败响应分开维护，避免错误 detail 微调导致大面积无效 diff。
- 只覆盖外部 HTTP / JSON 契约，不把 core 内部对象结构当作对外契约。

内部兼容影响面盘点：

- 列出本仓仍引用旧 artifact、旧包名、旧 SPI 的 module。
- 明确是否允许短期 bridge / adapter；若允许，必须标明后续删除闭环。
- 若不允许兼容层，则该闭环必须一次性完成全仓引用迁移，并通过全仓编译。
- 提供 compile migration test、依赖树检查或 Maven Enforcer 规则，防止旧 artifact 回流。

验收：

- 契约测试或 snapshot 测试随 `mvn test` 执行。
- 每次迁移旧包名、旧类型或旧模块时，同步更新替代表。
- 删除旧 module 前，契约测试、starter 启动测试和全仓编译必须通过。
- 破坏内部兼容的 PR 必须附带影响面列表和替代表更新。

### 1. 修复 JDBC 默认写入链测试失败

当前 `mvn -q test` 在 `ent-loom-crud-engine-jdbc` 失败，`DefaultEngineSingleTableCrudTest` 有 3 个用例报错：

```text
创建载荷包含不可写字段: id
```

触发原因是 `JdbcCrudCommandHandler` 在 create 路径中先把 `WriteCommand.id` 放回 payload，再由 `validateCreateFields` 禁止 payload 包含 `id`。这说明主键语义没有被清楚建模。

建议重构：

- 将 `id` / 主键从普通 writable field 中剥离，建模为 `identity` / `idPolicy`。
- 优先明确两种 create：外部显式指定主键、数据库生成主键；应用生成主键可先定义策略，后续补生成器 SPI。
- `CREATE` 校验只校验普通写入字段；主键校验由 identity policy 负责。
- `SAVE_OR_UPDATE` / batch 复用同一 identity 规则，避免 create/update 子命令重复拼装。

第一闭环只处理单表、单列主键，先恢复当前 JDBC 默认写入链。主键策略建议先收敛为：

| idPolicy | create 入参 | JDBC 行为 | 备注 |
| --- | --- | --- | --- |
| `EXPLICIT` | 必须传 id | id 参与 insert | 外部系统指定主键 |
| `GENERATED` | 不允许传 id，或按配置允许传入但 fail-fast | insert 后读取 generated key 并回填结果 | 数据库自增 / sequence |
| `APPLICATION` | 可不传 id | insert 前由应用生成器填充 | 若当前没有生成器 SPI，本闭环只声明策略，不接入默认实现 |
| `COMPOSITE` | 暂不支持 | 启动期或命令 normalize 阶段 fail-fast | 避免在本轮混入联合主键复杂度 |

约束：

- `payload` 中出现主键字段时，不再由普通 writable 校验处理，而是交给 `identity` 校验。
- `WriteCommand.id` 和 `payload[idField]` 同时存在时必须一致；不一致 fail-fast。
- `GENERATED` 策略下需要明确 JDBC generated key 回填方式；无法回填时返回清晰错误，不静默成功。
- `SAVE_OR_UPDATE` 判断 create / update 时只依赖统一 identity 规则，不复制 create/update 的 payload 拼装逻辑。

验收：

- 全仓 `mvn -q test` 通过。
- 显式传 `id` 创建、自动生成 `id` 创建、`SAVE_OR_UPDATE_BATCH` 均有覆盖。
- 非法主键字段、未知字段、不可写字段分别有明确错误。
- 联合主键在当前闭环内明确 fail-fast 或标记 unsupported，不做半支持。

### 2. 定义单一 `CrudRuntimeModel`，收敛运行期元数据入口

设计上允许两条声明路线并进：

- `ent-loom-meta-annotations`：作为复合能力入口，表达实体、字段、关系、展示、文档、DDL、UI 等跨组件共享语义。
- `ent-loom-crud-annotations`：作为 CRUD 原子能力入口，表达 CRUD-only 轻量接入和 CRUD 最强标注能力。

问题不在于存在两套注解，而在于重构前曾存在旧 CRUD 反射注册表和新 Meta Adapter 两条运行期入口：

- `CrudNativeRuntimeModelParser -> CrudRuntimeModelBackedEntityMetaRegistry`
- `MetaCrudAdapter -> ResourceCatalogAdapter.runtimeModel() -> CrudRuntimeModelBackedEntityMetaRegistry`

同一实体可能被两套解析规则解释，导致字段能力、关系图、resourceCode、诊断规则不一致。

本闭环先定义统一运行时模型和合并规则，再让后续 frozen registry 基于该模型构建。不要先把旧 `ReflectiveEntityMetaRegistry` 简单冻结化，否则会把旧入口的语义债务固化进新架构。

建议重构：

- 统一链路为 `Meta annotations + CRUD native annotations -> CrudRuntimeModel -> EntityMetaCatalog`。
- `@EntEntity` / `@EntField` 等 Meta 注解进入 Meta descriptor 后参与合并。
- `@EntCrud*` 注解继续保留，并通过 native parser 参与合并，支持 CRUD-only 和 Meta-first 两种使用方式。
- 对两套注解都能表达的属性建立明确优先级和冲突诊断，不允许静默产生两份运行期解释。
- 删除 CRUD core 内直接反射解析实体的最终运行入口；若迁移期保留 adapter，只能作为 `CrudRuntimeModel` 输入，不能作为运行期查询入口。
- Spring 装配最终只注册一个 `EntityMetaRegistry`，不再在没有 adapter 时回退到旧反射 registry。

建议拆成三个小闭环：

1. 模型闭环：新增或明确 `CrudRuntimeModel` 的 entity、field、identity、relation、capability metadata 结构，不删除旧入口。
2. 合并诊断闭环：实现 Meta-first、CRUD-only、Meta + CRUD 的 merge 优先级和冲突诊断。
3. 入口切换闭环：Spring 只暴露统一 `EntityMetaRegistry`，旧反射入口只能作为 parser / adapter 输入，随后删除。

验收：

- 所有实体元数据只从一个 frozen catalog 读取。
- Meta-first、CRUD-only、Meta + CRUD 覆盖三种声明方式都能生成同一种 `CrudRuntimeModel`。
- CRUD / DOC / DDL adapter 的来源优先级和冲突诊断一致。
- 重复 resourceCode、关系目标缺失、字段缺失在启动期 fail-fast。

### 3. Registry 改为启动期构建、冻结、只读快照

重构前问题：

- 旧反射 registry 使用可变 `HashMap` 保存元数据。
- `getRelationGraph(rootType)` 每次请求动态 BFS。
- 注册、校验、查询混在同一个运行期对象中。

建议重构：

```text
Meta annotations + CRUD native annotations
  -> parse / merge / diagnose
  -> CrudRuntimeModel
  -> RegistryBuilder
  -> validate resource / field / relation
  -> build indexes
  -> precompute relation graphs
  -> FrozenEntityMetaRegistry
```

`FrozenEntityMetaRegistry` 建议持有：

```text
Map<Class<?>, EntityMeta> entityMetas
Map<String, Class<?>> resourceTypes
Map<Class<?>, RelationGraph> relationGraphs
Map<Class<?>, Map<String, String>> columnToFields
```

设计要求：

- registry 暴露只读查询方法，不暴露 `register*`。
- 内部集合全部不可变。
- `RelationGraph` 和 `RelationEdge` 也应避免运行期可变。
- 默认启动期预计算 `rootType -> RelationGraph`。
- 如果后续实体规模很大，可只对派生结果做 lazy cache，但底层 `entityMetas` / `relationEdges` 必须已经冻结。

小闭环边界：

- 本闭环只让 registry 构建、索引和查询只读化，不再改注解语义。
- registry builder 只消费上一个闭环输出的 `CrudRuntimeModel`，不重新反射解释实体。
- 如果为了迁移保留旧 parser，旧 parser 的输出也必须先转换为 `CrudRuntimeModel`，不能直接注册到 frozen registry。

关于 `entityCodes` 请求：

冻结 registry 后仍支持这类查询：

```json
{
  "entityCodes": [
    "BusMoralRecordBatch",
    "BusMoralRecordLine",
    "BusMoralRecordMedia",
    "BusStudent"
  ],
  "options": {
    "page": 1,
    "limit": 10,
    "filter": {
      "status": "ACTIVE"
    }
  }
}
```

原因是 `entityCodes` 是运行期从已注册实体中选择关系查询范围，不是运行期注册新实体。改造后链路应为：

```text
HTTP entityCodes
  -> resolveEntityType(code)
  -> QuerySpec.rootType / entityClasses
  -> frozen relationGraph(rootType)
  -> PathResolver 校验关系范围
  -> QueryEngine 生成查询计划
```

约束：

- `entityCodes[0]` 必须等于路由实体。
- 所有 entity code 必须启动期已注册。
- 关系路径必须落在 root relation graph 内。
- 路径歧义时应 fail-fast，要求显式 `expandRelations` 或定制 QueryHandler。
- `options.filter.status` 默认仍是根实体字段过滤；子实体过滤应使用明确字段路径。

### 4. 明确 capability / operation / scene 矩阵

在继续扩展 Stats、Import、Export 前，需要先固定能力维度，避免路由、权限、审计、幂等和默认引擎选择继续依赖松散 operation。

建议模型：

```text
capability = QUERY | COMMAND | STATS | IMPORT | EXPORT
operation  = capability-scoped operation
scene      = 稳定业务语义，不承载页面临时状态或一次性筛选条件
```

P0 闭环先固化已经存在主链的 `QUERY / COMMAND / STATS`，并预留 `IMPORT / EXPORT` 的 capability 扩展点。Import / Export 的完整 operation 状态机不要在 P0 强行定死，应在其 core 抽象和异步任务模型明确后再固化。

P0 固化矩阵：

| Capability | 合法 Operation |
| --- | --- |
| `QUERY` | `PAGE / LIST / DETAIL / FIND_ONE` |
| `COMMAND` | `CREATE / UPDATE / DELETE / SAVE_OR_UPDATE / CREATE_BATCH / UPDATE_BATCH / DELETE_BATCH / SAVE_OR_UPDATE_BATCH / ACTION` |
| `STATS` | `QUERY / PREVIEW` |

Import / Export 已通过 capability-scoped operation 预留，不再使用全局无约束 operation：

| Capability | 候选 Operation |
| --- | --- |
| `IMPORT` | `VALIDATE / SUBMIT / COMMIT / CANCEL / STATUS / DOWNLOAD_ERROR` |
| `EXPORT` | `SUBMIT / DOWNLOAD / STATUS / CANCEL / PREVIEW` |

约束：

- `CrudSceneKey` 继续保留实体维度，不能简化为纯 `Capability + Operation + Scene`。
- 非空 `scene` 未命中必须 fail-fast；空 `scene` 才允许走默认引擎。
- 默认引擎必须显式声明支持的 `Capability + Operation`。
- 权限、审计、幂等 key 和错误信息都应记录 `capability`、`operation`、`scene` 和实体维度。
- `STATS` 独立成一等 capability，不再作为普通 Query operation。
- Import / Export 在 P0 只要求不新增全局无约束 operation；正式 operation 必须等异步任务、文件、事务和错误模型一起评审。

验收：

- Query、Command、Stats 至少覆盖 normalize 失败、治理拒绝、scene miss、默认引擎不支持、执行成功、执行失败审计。
- 旧 HTTP 路由可以保持不变，但内部必须映射到能力矩阵。
- 新增 Import / Export 时必须挂到 capability-scoped operation 扩展点，不新增全局无约束 operation。

## P1：收口架构边界

### 5. 拆薄 `ent-loom-crud-core`

当前 `crud-core` 类数量和行数过大，并且包含默认实现：

- `DefaultImportEngine`
- `DefaultExportEngine`
- `DefaultStatsQueryEngine`
- 本地 `TaskService` / `FileService`
- 导出渲染和列解析实现

建议重构：

- `core` 只保留契约、Spec、Gateway、治理、Validator、Scene SPI、Foundation 抽象。
- JDBC / SQL 相关实现留在 `engine-jdbc`。
- Excel / 文件格式实现留在 `import-export-excel`。
- 本地任务和文件服务移到默认实现模块，或明确归属 starter 的开发模式实现。
- Import / Export 在 core 中只定义 `ImportSpec / ExportSpec`、Gateway、Handler、Engine SPI、格式描述、Task/File 抽象和事务 SPI。
- `core` 不硬编码 `EXCEL / XLSX` 这类具体格式 enum，使用开放的 `ImportFormatDescriptor / ExportFormatDescriptor`。
- `core` 内部 `Result / Task / FileRef` 不直接作为 HTTP JSON 契约暴露，由 starter 的 Assembler / ResponseBuilder 转换为 api DTO。

验收：

- `core` 不承载重实现，不依赖外部技术栈。
- `core` 中 `capability.*` 包只放能力抽象和主链编排。
- Excel 模块缺失时，Import / Export core 抽象和非 Excel CRUD 能力仍可启动。
- 默认实现可替换，且替换不需要改 core。

### 6. 统一 Spring Boot Starter 包名和装配入口

当前 starter 同时存在：

- `com.entloom.crud.spring.config`
- `com.entloom.crud.starter.config`

这会让后续 AutoConfiguration 顺序、条件装配和文档表达持续混乱。

建议重构：

- 统一到 `com.entloom.crud.starter.*`。
- 删除 `com.entloom.crud.spring.config` 旧包名。
- 所有 `AutoConfigureBefore` / `AutoConfigureAfter` 指向真实 starter 入口。
- `CrudAutoConfiguration` 作为唯一自动配置入口。

小闭环边界：

- 第一闭环只统一自动配置入口和 resources，不同时拆 core 或改业务运行语义。
- 第二闭环迁移包名引用，删除旧 `com.entloom.crud.spring.config` 包。
- 第三闭环补 starter 包名 ArchUnit 守卫和 Spring Boot 2 / 3 装配测试。

验收：

- starter main 源码不再出现 `com.entloom.crud.spring` 包。
- Spring Boot 2 / 3 auto configuration resources 保持可用。
- 核心 Bean 装配链有测试覆盖。

### 7. 收窄注解模块依赖

当前 `ent-loom-crud-annotations` 依赖 `crud-api`、`meta-enums`、`meta-contract`，业务实体一旦使用注解会拖入较重契约。

建议重构：

- 注解模块只保留注解和必须 enum。
- 尽量避免依赖 `crud-api` 和 `meta-contract`。
- 若 enum 需要跨组件共享，优先放入轻量 `meta-enums` 或单独 contract-lite。

小闭环边界：

- 第一闭环先输出依赖树基线，列出注解模块当前传递依赖。
- 第二闭环只迁移 enum / contract-lite，不改注解语义。
- 第三闭环补 compile test 或 dependency guard，验证业务实体仅引用注解时不会带入 core / starter / engine。

验收：

- 业务实体仅使用 CRUD 注解时，不引入 core / starter / engine。
- 注解模块没有运行时重依赖。

## P2：补强质量治理

### 8. 增加架构守卫

现有 ArchUnit 已覆盖部分 query / command 和 core 技术栈依赖，但还不够。

建议新增守卫：

- `core` 不允许出现默认重实现命名，例如 `Default*Engine` 中含外部执行逻辑。
- `annotations` 不允许依赖 `crud-core`、`starter`、`engine-jdbc`。
- starter main 包名只能是 `com.entloom.crud.starter..`。
- 运行期 registry 不允许暴露 `register*`。
- 元数据运行入口只能来自统一 catalog，不允许新增反射 registry。
- `core` 不能出现 Spring、JDBC、POI、OSS、MinIO、web DTO 等实现依赖。
- starter 默认不能传递 Excel / POI；若短期 optional 依赖 Excel，非 Excel 自动配置类和 Bean 方法签名不能直接引用 Excel / POI 类型。
- `capability.importing` 不直接依赖 `capability.command` 的具体实现；`capability.exporting` 不直接依赖 `capability.query` 的具体实现。
- 阻断旧 artifact 和旧包名回流，例如 `stats-core`、`stats-engine-jdbc`、`relation-query`、`spring`、旧 Handler SPI、`QueryOperation.STATS`。
- HTTP DTO 不能直接暴露 core 内部 `Task / FileRef / Result` 类型。

验收：

- 新增 ArchUnit 测试随 `mvn test` 执行。
- Maven Enforcer、依赖树测试或 compile migration test 能阻断旧模块和重依赖回流。
- 违反模块边界时测试直接失败。

### 9. 弱化 ThreadLocal 上下文为边界适配

当前 `CrudRequestContextHolder` 依赖 `ThreadLocal` 保存请求属性。它适合 Web 同步边界，但不适合作为 Import / Export 异步任务、审计重放和后台执行的主上下文模型。

建议重构：

- 主链显式传递 `CrudInvocationContext` / `SubjectContext` / `AccessEntry` 快照。
- ThreadLocal 只用于 Web adapter 的短生命周期桥接。
- 异步任务启动时持久化上下文快照，执行时重新进入治理和审计主链。

验收：

- Import / Export 异步任务不依赖调用线程 ThreadLocal。
- 审计事件能记录完整 subject、operation、routeKey、scope。
- ThreadLocal 泄漏有测试覆盖。

## 建议执行顺序

1. 契约清单闭环：冻结外部 HTTP / JSON / 错误码 / 配置 key 清单，不改生产代码。
2. 契约测试闭环：建立 snapshot / 契约测试，明确动态字段忽略规则。
3. 迁移替代表闭环：盘点旧 Java 包名、Maven artifact、旧 SPI 和本仓影响面。
4. JDBC 主键闭环：修复 create / saveOrUpdate identity 规则，先恢复当前失败测试和全仓测试。
5. Runtime Model 闭环：定义 `CrudRuntimeModel`、merge 优先级和冲突诊断，先不搬模块。
6. 入口收敛闭环：旧反射 parser 只能输出 `CrudRuntimeModel`，Spring 运行期只暴露一个 `EntityMetaRegistry`。
7. Frozen Registry 闭环：落地 `RegistryBuilder + FrozenEntityMetaRegistry`，保留现有外部 HTTP 行为。
8. 能力矩阵闭环：固定 Query / Command / Stats 的 `capability / operation / scene`，Import / Export 只预留扩展点。
9. Core 边界闭环：拆薄 `crud-core` 默认实现，并先定义 Import / Export 抽象边界。
10. Starter 闭环：统一 starter 包名和 AutoConfiguration。
11. Annotations 闭环：收窄 annotations 依赖。
12. 守卫闭环：补 ArchUnit / Enforcer / 依赖树守卫和异步上下文治理。
