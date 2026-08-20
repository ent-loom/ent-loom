# ent-loom

ent-loom 是一个模块化、元数据驱动的 Java 后端框架。它通过可独立运行的组件模型和统一治理主链，为 Query、Command、Stats、Import、Export，以及后续 DDL、DOC、UI 投影提供基础能力。

项目当前处于框架能力收敛阶段，优先稳定建模、治理、公开契约和模块边界，不以兼容所有历史 API 为目标。

## 核心架构

ent-loom 包含两条相互独立、通过组件 Runtime Model 连接的主链。

### 元数据建模

```text
Java 实体 + Meta/Module 注解 + Convention
  -> 属性贡献、裁决与诊断
  -> Module Runtime Model
  -> CRUD / DDL / DOC / UI 消费者
```

Meta 不是所有组件必须依赖的统一运行时模型，而是一套可选的通用语义输入：

- CRUD、DDL、DOC、UI 保留自己的注解、Parser 和 Runtime Model，可以独立运行。
- 启用 Meta 后，Adapter 将通用 Descriptor 投影到目标组件模型。
- 多个来源按属性独立裁决，不整模型覆盖，也不依赖 Bean 加载顺序解决冲突。
- 运行时只消费各组件拥有的 Runtime Model。

当前已实现 Meta -> CRUD / DOC；Project Convention、统一属性级 Resolver、DDL/UI Adapter 仍在演进中。完整规则见 [元数据约定与裁决契约](./docs/architecture/core/metadata-resolution-contract.md)。

### CRUD 治理执行

```text
HTTP / SDK / 业务调用
  -> 不可变 Spec
  -> Normalize
  -> Govern
  -> Scene Handler 或 Engine
  -> Audit / Result
```

通过内置 Gateway 的请求必须经过主体、权限、数据范围和审计主链。直接调用底层 Engine 不自动获得治理保证。详细边界见 [CRUD 架构入口](./docs/architecture/components/crud/index.md)。

## 当前能力

| 能力 | 状态 | 边界 |
|---|---|---|
| Meta Parser / Descriptor / Diagnostic | 已实现 | Project Convention 尚未完整闭环 |
| Meta -> CRUD / DOC Adapter | 已实现 | DDL Adapter 为空实现，UI Adapter 尚未建立 |
| Query / Command / Stats | 已实现 | 默认关系读为 `ROOT_FIRST` |
| Import / Export | 已实现小文件同步闭环 | 异步 Worker、Streaming 和大文件能力待补 |
| CRUD Governance | 已实现默认主链 | 业务必须提供真实主体、权限和范围实现 |
| Default JDBC Engine | 已实现 | 不作为通用 ORM 或任意 SQL 平台 |
| 强类型业务 Handler | 部分实现 | 稳定 `UpdatePatch<T>` API 待闭环 |
| DDL | Core 与 Spring 集成已存在 | Meta 投影待实现 |
| DOC | Core 与 Meta Adapter 已存在 | OpenAPI 投影不在当前闭环 |
| UI | 基础 Contract 已存在 | Meta Adapter 和默认渲染未实现 |

尚未完成的工作统一维护在 [当前实施总览](./docs/evolution/roadmap/current.md)，不在 README 中重复展开。

## 仓库结构

```text
ent-loom-base                  公共轻量类型
ent-loom-meta                  Meta 聚合与叶子模块
  ent-loom-meta-contract       Descriptor、来源和诊断契约
  ent-loom-meta-annotations    通用语义注解
  ent-loom-meta-core           Meta 解析与 Descriptor
ent-loom-modules
  ent-loom-crud                数据操作、治理、JDBC、Excel、Starter
  ent-loom-ddl                 DDL 模型、执行与 Spring 集成
  ent-loom-doc                 文档模型与扩展 SPI
  ent-loom-ui                  UI Schema 基础契约
ent-loom-integrations          Meta -> Module Adapter
```

业务模块应按职责依赖具体叶子模块，不依赖聚合 POM 作为运行时 API。模块所有权和依赖方向见 [组件边界与依赖规则](./docs/architecture/core/component-boundaries.md)。

## 构建与验证

要求：

- JDK 21 或更高版本
- Maven 3.9 或更高版本

```bash
export JAVA_HOME=/path/to/jdk-21
mvn clean test
```

当前完整 Reactor 以 Java 21 为统一编译目标，使用 Spring Boot 3.5 / Spring Framework 6.2。Boot 4、Boot 2 和低版本 Java 兼容线仍属于后续演进，不代表当前已支持。

## 使用与文档

- [文档中心](./docs/index.md)：文档结构和推荐阅读路径。
- [系统架构总览](./docs/architecture/overview.md)：模块、建模与执行关系。
- [Core Contract](./docs/architecture/core/index.md)：跨模块稳定契约。
- [CRUD 业务集成模板](./docs/guides/crud/integration-template.md)：业务项目接入方式。
- [Meta-first 最佳实践](./docs/guides/meta/meta-first.md)：通用语义声明与 Adapter 使用。
- [设计决策](./docs/evolution/decisions/index.md)：关键取舍及其状态。
- [路线图](./docs/evolution/roadmap/index.md)：当前和后续工作。
