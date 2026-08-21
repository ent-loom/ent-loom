# Java 与 Spring 兼容性路线图

> 状态：Remaining
> 规范决策：[Java 运行时与 Spring 兼容性](../../decisions/core/Java运行时与Spring兼容性.md)

当前完整 Reactor 使用 JDK 21、Spring Boot 3.5 和 Spring Framework 6.2。Java 8 核心、Boot 2
兼容线及 Boot 4 主线尚未完成，不能作为当前支持能力声明。

当前对外支持：完整 Reactor 使用 JDK 21+。当前主开发和构建 JDK 为 21；未来按模块提供 Core/Boot 2 的 Java 8 目标线和 Boot 3/4 的 Java 17 目标线。

## 当前差距

| 领域 | 差距 |
|---|---|
| 核心模块 | 尚未逐模块证明 `release=8`，第三方依赖和 JDK API 边界未审计完成 |
| JDBC | `ent-loom-crud-engine-jdbc` 仍包含 Spring JDBC 使用点 |
| Spring 集成 | CRUD、Meta、DDL 尚未形成明确的 Spring 5/6/7 独立版本线 |
| 构建验证 | 尚未建立编译 JDK、字节码目标和运行 JDK 分离的 CI 矩阵 |
| 发布 | Boot 2、3、4 的 artifact、BOM 与支持周期尚未定稿 |

## 实施阶段

### P0：兼容性基线

- 登记每个发布模块的 JDK API、字节码目标、Spring/Servlet 依赖和测试入口。
- 确认可使用 `release=8` 的核心模块，并审计 Lombok、Jackson、ArchUnit 等依赖边界。
- 明确公共依赖管理与各 Spring 版本线 BOM 的责任边界。
- 提交 Maven Wrapper 固定 Maven 3.9.12；`.java-version=21` 仅作本地提示，并保持根 POM 默认 `release=21` 和完整 Reactor 的 JDK 21 Enforcer。

### P1：核心边界

- 为 Core 增加禁止依赖 Spring、Servlet 和 Starter 的架构守卫。
- 识别原生 JDBC 与 Spring JDBC 的真实拆分边界。
- 保证 Meta Adapter 只依赖目标组件 Core，不反向带入 Spring。

### P2：Boot 4 主线

- Boot 4 / Spring 7 集成层使用 Java 17 字节码目标，由 JDK 21 构建。
- 完成依赖树、`jakarta.*`、启动、Web、事务和 JDBC 迁移验证。
- 在 JDK 17、21、25 上运行集成测试；Boot 3.5 在迁移期间保持独立可验证。

### P3：Boot 2 兼容线

- 使用独立 Spring 5.3 / Boot 2.7 依赖管理和 artifact。
- 仅为有真实需求的 CRUD、Meta、DDL 集成点提供 Boot 2 构件。
- 验证 Java 8 编译和最小应用运行，不与 `jakarta.servlet` 构件共用坐标。

### P4：发布与持续验证

- 建立核心、Boot 2、Boot 3/4 分离的编译和运行矩阵。
- 增加最小应用、依赖树和字节码版本检查。
- 发布前确定 BOM、版本号、分支策略和支持周期。

## 验收条件

1. 每个发布构件都有明确的最低 Java、Spring、Boot 和 Servlet 边界。
2. Core 不直接依赖 Spring、Starter 或 Servlet API。
3. Boot 2 与 Boot 3/4 不共享包含不兼容传递依赖的 Maven 坐标。
4. 编译验证与运行验证分别覆盖对应 JDK 矩阵。
5. 文档、POM、CI 和示例应用的支持范围一致。

在 P0 审计完成前不创建候选空模块，也不承诺所有 DDL、DOC、UI 能力提供 Boot 2 版本。
