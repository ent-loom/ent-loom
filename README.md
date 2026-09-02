# ent-loom

ent-loom 是一个模块化、元数据驱动的 Java 后端框架，面向实体建模与业务能力扩展，
提供可独立运行、按需组合的组件。

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

完整规则见 [元数据约定与裁决契约](./docs/architecture/core/元数据约定与裁决契约.md)。

## 当前能力

| 归属组件         | 能力域                              | 状态         | 边界                               |
|--------------|----------------------------------|------------|----------------------------------|
| Meta         | Parser / Descriptor / Diagnostic | 已实现        | Project Convention 已接入属性级裁决；动态刷新不在当前闭环 |
| Integrations | Meta -> CRUD / DOC / DDL 静态 Adapter | 已实现        | UI Adapter 尚未建立 |
| CRUD         | Query / Command / Stats          | 已实现        | 默认关系读为 `ROOT_FIRST`              |
| CRUD         | Import / Export                  | 小文件同步闭环已实现 | 异步 Worker、Streaming 和大文件能力待补     |
| CRUD         | Task / File                      | 基础能力已实现    | 对象存储、清理 Worker 待补                |
| CRUD         | Governance                       | 默认主链已实现    | 业务必须提供真实主体、权限和范围实现               |
| CRUD         | Default JDBC Engine              | 已实现        | 不作为通用 ORM 或任意 SQL 平台             |
| CRUD         | 强类型业务 Handler                    | 基础能力已实现 | 稳定 `UpdatePatch<T>` 已具备；业务 Handler 按实体接入 |
| DDL          | Core / Spring                    | 已实现        | MySQL 8 建表、受控差异与 Meta 投影已验证；不作为通用迁移平台 |
| DOC          | Core / Meta Adapter              | 已实现        | OpenAPI 投影不在当前闭环                 |
| UI           | 基础 Contract                      | 已实现        | Meta Adapter 和默认渲染未实现            |

尚未完成的工作统一维护在 [当前实施总览](./docs/evolution/roadmap/当前实施总览.md)，不在 README 中重复展开。

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

业务模块应按职责依赖具体叶子模块，不依赖聚合 POM 作为运行时
API。模块所有权和依赖方向见 [组件边界与依赖规则](./docs/architecture/core/组件边界与依赖规则.md)。

## 构建与验证

| 口径 | JDK | Spring Boot | Spring Framework |
|---|---:|---|---|
| 当前完整 Reactor | 21+ | 3.5.x | 6.2.x |
| 目标框架无关 Core | 8 | - | - |
| 目标 Boot 2 集成线 | 8 | 2.7.x | 5.3.x |
| 目标 Boot 3 / Boot 4 集成线 | 17+ | 3.5.x / 4.x | 6.2.x / 7.x |

当前完整 Reactor 的默认开发、构建和验证基线为 JDK 21。推荐使用 `./mvnw test`，Wrapper 固定 Maven 3.9.12，Enforcer 校验构建 JDK 21+；`.java-version` 仅作本地版本提示。Java 8、Boot 2 和 Boot 4 属于分阶段目标兼容线，尚不代表当前全仓支持；详细边界见
[Java 运行时与 Spring 兼容性版本线](./docs/evolution/decisions/core/Java运行时与Spring兼容性.md)。

## 使用与文档

- [Docusaurus 文档站](./docs-site/README.md)：独立展示工程、本地启动和静态构建方式。
- [文档中心](./docs/index.md)：文档结构和推荐阅读路径。
- [开发环境与 JDK 管理](./docs/guides/开发环境与JDK管理.md)：多 JDK、jenv、Maven Wrapper 和 IDEA 配置。
- [Maven Central 发布](./docs/guides/Maven%20Central发布.md)：IDEA 开发后的版本升级、签名上传和 Central 确认流程。
- [系统架构总览](./docs/architecture/架构总览.md)：模块、建模与执行关系。
- [Core Contract](./docs/architecture/core/index.md)：跨模块稳定契约。
- [CRUD 业务集成模板](./docs/guides/crud/业务集成模板.md)：业务项目接入方式。
- [Meta-first 最佳实践](./docs/guides/meta/Meta优先指南.md)：通用语义声明与 Adapter 使用。
- [DDL 实施清单](./docs/evolution/roadmap/ddl/DDL实施清单.md)：实体到 MySQL 8 的阶段路线与验收门禁。
- [版本事实收敛与全链路验收实施计划](./docs/evolution/roadmap/版本事实收敛与全链路验收实施计划.md)：跨模块当前事实与复用验收证据。
- [设计决策](./docs/evolution/decisions/index.md)：关键取舍及其状态。
- [路线图](./docs/evolution/roadmap/index.md)：当前和后续工作。
