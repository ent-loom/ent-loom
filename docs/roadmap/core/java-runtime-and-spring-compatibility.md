# Java 运行时与 Spring 兼容性路线图

## 状态

规划中，尚未开始兼容线迁移。规范性决策见[Java 运行时与 Spring 兼容性版本线](../../decisions/core/java-runtime-and-spring-compatibility.md)。

当前仓库仍以 Java 25 + Spring Boot 3.5 / Spring Framework 6.2 为唯一构建基线。本路线图补充目标结构和验收方式，不表示 Java 8 或 Spring Boot 2 已获得支持。

## 目标版本矩阵

| 版本线 | 编译目标 | 运行验证 | Spring 生态 | Web 命名空间 | 目标构件形态 |
|---|---:|---|---|---|---|
| 共享核心 | Java 8 | JDK 8；在完整 Reactor 中由 JDK 17/21 构建 | 不依赖 Spring | 无 | `base`、Meta/CRUD/DDL/DOC/UI 的 API、契约、模型和核心 |
| Boot 2 兼容线 | Java 8 | 至少 JDK 8 | Spring 5.3 + Boot 2.7 | `javax.servlet` | 独立的 Spring 5 适配层和 Boot 2 Starter |
| Boot 3 主线 | Java 17 | JDK 17、21、25 | Spring 6.2 + Boot 3.5 | `jakarta.servlet` | 当前 Starter 坐标继续作为 Boot 3 主线 |

“编译目标”是模块的 `maven.compiler.release`，不等同于构建 Maven Reactor 使用的 JDK。完整 Reactor 仍应使用 JDK 17 或 21 执行。

## 当前仓库映射

以下是逻辑角色到现有模块的初始映射。标记为“候选”的内容只用于版本规划，不要求现在创建空目录或空 Maven 模块。

| 逻辑角色 | 当前模块 | 目标处理 | 优先级 |
|---|---|---|---|
| 公共核心 | `ent-loom-base` | 作为 Java 8 候选核心，先做 API 和第三方依赖审计 | P0 |
| Meta 核心 | `ent-loom-meta-enums`、`ent-loom-meta-contract`、`ent-loom-meta-annotations`、`ent-loom-meta-core` | 保持框架无关，目标 `release=8` | P0 |
| CRUD 核心 | `ent-loom-crud-api`、`ent-loom-crud-annotations`、`ent-loom-crud-core` | 保持框架无关，目标 `release=8` | P0 |
| DDL / DOC / UI 核心 | 各自的 API、annotations、core 模块 | 按实际 API 使用情况评估 Java 8，不因规划提前承诺 | P0 |
| JDBC 执行层 | `ent-loom-crud-engine-jdbc` | 候选拆为原生 JDBC 核心和 Spring JDBC 适配层 | P1 |
| CRUD Boot 3 | `ent-loom-crud-spring-boot-starter` | 保留现有 artifact，迁移到 `release=17` 后验证 JDK 17/21/25 | P2 |
| Meta Boot 3 | `ent-loom-meta-spring-boot-starter` | 纳入 Boot 3 版本线和启动测试 | P2 |
| DDL Spring / Boot 3 | `ent-loom-ddl-spring`、`ent-loom-ddl-spring-boot-starter` | 明确 Spring 6 / Boot 3 归属，补充版本线测试 | P2 |
| Boot 2 集成层 | 上述 Spring 集成模块的独立兼容构件 | 候选增加带 `boot2` 的独立坐标，使用 `javax.servlet`；不与 Boot 3 Starter 共用坐标 | P3 |
| Meta adapter | `ent-loom-meta-adapter-*` | 保持目标 core 依赖；只有引入 Spring 后才拆版本线 | P1 |

当前 `ent-loom-crud-spring`、`ent-loom-crud-relation-query`、`ent-loom-crud-stats-core`、`ent-loom-crud-stats-engine-jdbc` 和 `ent-loom-crud-demo` 等目录不作为本路线图的已规划模块。它们在具有明确职责、POM、测试和依赖边界前保持未聚合状态。

## 规划目录

目录表达逻辑边界，方括号中的内容表示状态：

```text
ent-loom
├── ent-loom-base                                      [现有，共享核心候选 Java 8]
├── ent-loom-meta
│   ├── ent-loom-meta-contract                          [现有，共享核心候选 Java 8]
│   ├── ent-loom-meta-annotations                       [现有，共享核心候选 Java 8]
│   ├── ent-loom-meta-core                              [现有，共享核心候选 Java 8]
│   ├── ent-loom-meta-spring-boot-starter               [现有，Boot 3]
│   └── ent-loom-meta-spring-boot2-starter              [候选，Boot 2]
├── ent-loom-modules
│   ├── ent-loom-crud
│   │   ├── ent-loom-crud-api / annotations / core      [现有，共享核心候选 Java 8]
│   │   ├── ent-loom-crud-engine-jdbc                   [现有，待拆分]
│   │   ├── ent-loom-crud-engine-jdbc-core              [候选，原生 JDBC]
│   │   ├── ent-loom-crud-engine-spring5                [候选，Spring 5]
│   │   ├── ent-loom-crud-engine-spring6                [候选，Spring 6]
│   │   ├── ent-loom-crud-spring-boot2-starter          [候选，Boot 2]
│   │   └── ent-loom-crud-spring-boot-starter           [现有，Boot 3]
│   └── ent-loom-ddl / ent-loom-doc / ent-loom-ui       [按各自集成需求演进]
└── ent-loom-integrations
    └── ent-loom-meta-adapter-*                         [现有，目标 core 适配，不因兼容线重复维护]
```

`engine-spring5`、`engine-spring6` 以及类似构件的最终命名，需要在 P0 依赖审计和 Maven 父子关系设计后确定。不能仅为了补齐目录而直接创建它们。

## 分阶段事项

### P0：建立兼容性基线

- [ ] 为每个叶子模块登记 Java API、字节码目标、Spring 依赖、Servlet 命名空间和测试入口。
- [ ] 区分构建 JDK、编译 `release` 和运行 JDK，形成可审查的模块矩阵。
- [ ] 确认哪些核心模块确实可以 `release=8`，同时检查 Lombok、Jackson、ArchUnit 等第三方依赖的版本边界。
- [ ] 明确根 POM 的公共依赖管理与 Spring 版本线依赖管理的责任边界。

### P1：收敛核心边界

- [ ] 为 `ent-loom-crud-core`、Meta core 等模块增加禁止依赖 Spring、Servlet 和 Boot starter 的架构守卫。
- [ ] 将 `ent-loom-crud-engine-jdbc` 中的 `spring-jdbc` 使用点分类，决定原生 JDBC 核心与 Spring 适配层的拆分边界。
- [ ] 保证 Meta adapter 只依赖目标能力的 core，不把 Spring 依赖反向带入核心模块。

### P2：收敛 Boot 3 主线

- [ ] 将可行的共享核心和 Boot 3 模块分别设置 `maven.compiler.release=8/17`。
- [ ] 将根 Enforcer 从全局 Java 25 调整为完整 Reactor 所需的最低构建 JDK 约束；具体改动以 P0 结果为准。
- [ ] 在 JDK 17、21、25 上执行 Boot 3 启动、Web、事务和 JDBC 烟囱测试。
- [ ] 用依赖树和启动测试确认 Boot 3 线只使用 Spring 6 / `jakarta.*`。

### P3：增加 Boot 2 兼容线

- [ ] 为 Boot 2 线建立独立的 Spring 5.3 / Boot 2.7 依赖管理入口。
- [ ] 为 CRUD、Meta、DDL 等实际 Spring 集成点分别确定是否需要 Boot 2 Starter。
- [ ] 验证 Boot 2 构件的 Java 8 编译和最小应用启动，不使用 Boot 3 Starter 的传递依赖。
- [ ] 用独立 artifact 坐标隔离 `javax.servlet` 与 `jakarta.servlet`，不使用 classifier 隐藏两套实现。

### P4：发布与持续验证

- [ ] 评估公共 BOM、Boot 2 BOM、Boot 3 BOM 的分层方式，避免根 POM 同时把两套 Spring BOM 当成全局默认。
- [ ] 建立 CI 矩阵：核心 / Boot 2 / Boot 3 分别对应编译和运行 JDK。
- [ ] 增加最小示例应用、依赖树检查和字节码版本检查。
- [ ] 在第一次兼容线发布前确定版本号、分支和支持周期规则；版本线不同不自动意味着拆仓库。

## 验收条件

兼容线迁移完成前，至少满足：

1. 每个发布构件都有明确的最低 Java、Spring、Boot 和 Servlet 边界。
2. 核心模块不直接依赖 Spring、Boot starter 或 Servlet API。
3. Boot 2 与 Boot 3 不共享包含不兼容传递依赖的 Maven 坐标。
4. 编译验证和运行验证分别覆盖对应 JDK 矩阵，而不是只验证一次 `mvn install`。
5. 文档、POM、CI 和示例应用中的当前支持版本保持一致。

## 暂缓事项

- 暂不创建没有职责、POM、源码和测试的空模块。
- 暂不实现 Boot 2 兼容代码，直到核心边界和依赖管理方案完成审计。
- 暂不承诺所有 DDL、DOC、UI 能力都提供 Boot 2 版本；以实际 Spring 依赖和使用场景为准。
- 暂不因为存在 Java 8 目标就修改当前 Java 25 + Boot 3.5 主线的运行行为。
