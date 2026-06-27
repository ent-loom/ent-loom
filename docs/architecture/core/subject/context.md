# 执行上下文与主体 (Context & Subject)

在 `ent-loom` 中，每一个请求的身份信息和执行状态都由专门的上下文对象承载，确保在分层架构中信息能安全、一致地传递。

## 1. 请求主体：`SubjectContext`

`SubjectContext` 定义了“谁”正在发起请求。它是治理逻辑的输入核心。

- **`subjectId`**: 调用者的唯一标识（如用户 ID、服务名）。
- **`tenantId`**: 多租户环境下的租户标识。
- **`orgId`**: 调用者所属的组织/部门标识。

这些信息通常由 `CrudSubjectResolver` 从安全框架（如 Spring Security, Shiro）或请求头中解析得到。

## 2. 执行上下文：`CrudExecutionContext`

`CrudExecutionContext` 是一个轻量级的只读接口，描述了当前操作的运行时状态。

- **`routeKey`**: 本次操作的路由标识（如 `student:PAGE:default`）。
- **`scene`**: 当前业务场景。
- **`startTimeMs`**: 操作开始的时间戳，用于性能监控和超时控制。
- **`attributes`**: 存储在整个生命周期内传递的扩展属性。

## 3. 请求上下文持有者：`CrudRequestContextHolder`

框架提供了一个基于 `ThreadLocal` 的持有者，用于在当前线程中存储和获取 `CrudInvocationContext`。

- **隐式传递**: 使得在 Service 深层或拦截器中无需显式传递参数即可获取当前 Subject。
- **安全清理**: 必须确保在请求结束时（如通过 Filter 或 Interceptor）调用 `clear()`，防止线程污染。

## 4. 治理快照：`GovernanceSnapshot`

对于异步任务（如导入导出），框架会将治理结果（Subject, Scope, Decision）序列化为快照。

- **环境重建**: 当异步任务被 Worker 执行时，框架会基于快照重建执行环境，确保异步执行时的权限检查与任务创建时刻完全一致。
- **防止篡改**: 异步任务不应重新调用 `SubjectResolver`，而应严格遵循快照中的身份信息。
