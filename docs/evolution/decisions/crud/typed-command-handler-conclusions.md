# 强类型命令 Handler 决策

> 状态：Accepted
> 当前契约：[CRUD 强类型边界](../../../architecture/components/crud/typed-boundary.md)

## 背景

业务 Handler 直接处理 `CommandSpec<Object>` 和字段 Map 时，会重复绑定、类型转换、事务包装和字段字符串判断。另一方面，并非所有更新都需要 PATCH 三态，强制所有业务使用 Patch 也会增加复杂度。

## 决策

业务入参按语义选择：

| 场景 | 推荐入参 |
|---|---|
| 常规实体式 CREATE/UPDATE | Entity 或明确请求 DTO |
| 真正局部更新 | `UpdatePatch<T>` |
| 主子表聚合更新 | Entity 聚合或聚合 Patch |
| submit/revoke/approve 等动作 | 专用 DTO |
| 框架默认引擎 | 规范化动态载荷 |

业务代码追求面向对象，但框架内部不追求消灭所有 Map。

## 模板职责

推荐的 Handler 模板负责：

- 从 Spec 绑定 Entity、DTO 或 Patch。
- 在 Spring 集成层提供统一事务边界。
- 统一包装 CommandResult。
- 保留必要的 before/after 或 delegate 扩展点。

业务 Handler 负责：

- 业务校验、状态流转和权限例外。
- 加载旧数据。
- 主表和子表的业务同步。
- 决定调用业务 Service 还是默认 delegate。

Core 模板不依赖 Spring；事务模板属于 Starter。构造器注入和业务 Service 不进入框架 Core Contract。

## 职责约束

1. Spec 不承担 payload 解析，动态载荷统一由 Binder 转为强类型视图。
2. Scene Handler 不直接依赖 JDBC 实现。
3. delegate 可以复用默认执行链，但不是业务逻辑的唯一写入方式。
4. 普通单表 Patch 不承担聚合关系同步职责。
5. 包结构只表达职责，不能代替模型所有权和依赖边界。

## Patch 的位置

`UpdatePatch<T>` 只用于需要区分 absent、explicit null 和 value 的场景，不作为 full update 默认入口。普通 Patch 与聚合 `EntityPatch<T>` 的命名和职责以强类型边界文档为准。

## 抽象停止条件

只有满足以下条件才继续增加公共父类或 Hook：

1. 至少两个到三个真实业务类出现相同结构。
2. 抽象后业务代码更短且语义更直接。
3. 事务、错误和返回语义能够统一。
4. 不要求业务为适配抽象增加更多配置。

不设计万能 CRUD 父类、通用子表同步 DSL 或把所有权限规则注解化。

## 后果

业务 Handler 获得编译期类型保护和统一事务入口；框架仍保留动态执行能力。代价是需要维护 Binder 与少量按场景区分的模板，而不是一个覆盖所有命令的抽象。
