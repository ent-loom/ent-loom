# Java 运行时与 Spring 兼容性版本线

## 决策状态

已接受，作为后续框架模块拆分、POM 调整和 CI 设计的约束。当前仓库仍处于 Java 25 + Spring Boot 3.5 基线；本文描述目标架构，不表示兼容线已经全部实现。

## 核心结论

框架不追求“一个构件支持所有 Java 版本”。核心能力与 Spring 集成层解耦，按 Spring 生态大版本提供独立适配线：

```text
共享核心模块                         尽量 Java 8
Spring Boot 2 兼容线                 Java 8 + Spring 5.3 + Boot 2.7 + javax.servlet
Spring Boot 3 主线                   Java 17 + Spring 6.2 + Boot 3.5 + jakarta.servlet
```

Boot 3 模块以 Java 17 作为编译基线时，可以运行在 Java 17、21、25。支持 Java 25 不等于必须使用 `--release 25` 编译。

## 约束原因

Java 字节码版本、JDK API 和 Spring 生态最低 JDK 共同决定运行边界：

| 技术线 | 最低 Java | Web 命名空间 | 定位 |
|---|---:|---|---|
| Spring Boot 2.7 / Spring 5.3 | Java 8 | `javax.servlet` | 存量系统兼容 |
| Spring Boot 3.x / Spring 6.x | Java 17 | `jakarta.servlet` | 新项目主线 |

`javax.servlet` 与 `jakarta.servlet` 是不同包名。Boot 2 和 Boot 3 的 Web Starter、依赖管理和自动装配不应放在同一个实现模块中。Java 8 也不能运行 Boot 3，因此不能通过降低根 POM 的 Java 版本实现全版本兼容。

## 模块边界

核心模块应尽量不直接依赖 Spring：

- Meta、CRUD API、领域模型、规则引擎和执行契约保持框架无关。
- JDBC 核心能力优先使用标准 JDBC 抽象；`JdbcTemplate` 放入 Spring 适配层。
- Spring IoC、事务、配置绑定、MVC、Servlet、文件上传下载和 Boot 自动装配只出现在集成模块。

后续模块形态建议为：

```text
ent-loom-core                         Java 8 目标
ent-loom-engine-jdbc-core             Java 8 目标，原生 JDBC
ent-loom-engine-spring5               Java 8 目标，Spring 5.3
ent-loom-spring-boot2-starter         Java 8 目标，Boot 2.7，javax.servlet

ent-loom-engine-spring6               Java 17 目标，Spring 6.2
ent-loom-spring-boot-starter           Java 17 目标，Boot 3.5，jakarta.servlet
```

上面的名称表达的是模块角色和依赖边界，不表示现在就新增同名构件。当前仓库的实际模块映射、候选拆分方式和落地阶段见[兼容性路线图](../../roadmap/core/java-runtime-and-spring-compatibility.md)。最终 artifact 名称应在完成依赖盘点和 Maven 父子关系设计后确定。

结合当前仓库时：

- `ent-loom-crud-core`、Meta core、各类 API/契约模块优先保持核心层边界。
- 当前 `ent-loom-crud-engine-jdbc` 中的 Spring JDBC 依赖，后续评估拆为原生 JDBC 核心和 Spring JDBC 适配。
- 当前 `ent-loom-crud-spring-boot-starter` 保留为 Boot 3 主线。
- Boot 2 使用独立 artifact，例如 `ent-loom-spring-boot2-starter`，不与 Boot 3 共用同一 Starter 坐标。

兼容性版本线的覆盖范围不只包括 CRUD：`ent-loom-meta-spring-boot-starter`、`ent-loom-ddl-spring` 和 `ent-loom-ddl-spring-boot-starter` 也属于 Spring 集成边界。它们在引入 Boot 2 兼容线时必须分别评估对应的 Spring 5 / Boot 2 构件；Meta adapter、DOC 和 UI 只有在实际引入 Spring 依赖时才进入对应版本线。

## 仓库与版本管理

Boot 2 和 Boot 3 兼容线放在同一个 Git 仓库和 IDEA 工作空间中，通过独立 Maven 模块、依赖管理和测试入口隔离：

- 共享核心只维护一份，避免两个仓库之间复制修复。
- 两条 Spring 线分别管理 Spring BOM、Servlet API、编译目标和测试依赖。
- 不使用同一 Maven 坐标或 classifier 强行承载 Boot 2/Boot 3 两套不兼容传递依赖。
- 发布后如生命周期不同，可以使用 `2.x`、`3.x` 分支；只有代码长期严重分叉、团队和发布节奏完全不同，才考虑拆仓库。

## Maven 与 IDEA 构建策略

编译目标、构建 JDK 和运行 JDK 必须分开定义：

| 场景 | 约束 |
|---|---|
| 完整 Maven Reactor | 使用 JDK 17 或 21 执行 `mvn install` |
| 核心 / Boot 2 产物 | 模块使用 `maven.compiler.release=8` |
| Boot 3 产物 | 模块使用 `maven.compiler.release=17` |
| Boot 3 运行验证 | JDK 17、21、25 |
| Java 8 运行验证 | 仅核心和 Boot 2 模块 |

一个完整 Reactor 可以在 JDK 17 或 21 下同时生成 Java 8 和 Java 17 字节码。不能在 JDK 8 下安装包含 Boot 3 的完整项目；JDK 8 只用于兼容模块的单独测试。

根 POM 的 Enforcer 应约束构建 JDK 至少为 Java 17，而不是全局强制 Java 25。每个模块通过 `maven.compiler.release` 声明自己的字节码目标。IDEA 的 Maven Importer 和 Maven Runner 应使用 JDK 17 或 21，CI 再按运行矩阵验证实际兼容性。

## 实施顺序

1. 先保持当前 Java 25 + Boot 3 主线可用，不在未拆分前引入 Boot 2 依赖。
2. 盘点核心模块的 JDK API、第三方依赖和 Spring 类型，确认 Java 8 可编译边界。
3. 将 `JdbcTemplate`、事务和 Web/Servlet 依赖收敛到适配层。
4. 将 Boot 3 模块编译目标调整为 Java 17，并验证 JDK 17、21、25。
5. 再增加独立 Boot 2/`javax.servlet` 兼容模块，验证 Java 8 运行。
6. 最后把各模块纳入对应的 Maven/CI 矩阵，避免把“编译成功”误认为“运行环境全部支持”。

## 落地追踪

本文只维护兼容性决策、边界和非目标。目标目录、模块映射、阶段优先级、暂缓事项和验收条件维护在[兼容性路线图](../../roadmap/core/java-runtime-and-spring-compatibility.md)；开始实际迁移后，再在 `docs/work/core/` 建立过程性实施清单。

## 非目标

- 不承诺支持所有历史 Java 小版本或所有 Spring 小版本。
- 不为了 Java 8 兼容而删除 Boot 3 的自动装配、事务、MVC 和配置绑定便利性。
- 不在同一个 Starter 中同时维护 `javax.servlet` 和 `jakarta.servlet` 两套 Web API。
