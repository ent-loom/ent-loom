# 执行上下文与主体

> 状态：Current
> 最近核验：2026-08-20

## `SubjectContext`

`SubjectContext` 描述谁发起调用，主要包含 `subjectId`、`tenantId` 和 `orgId`。它可以由上游显式传入，也可以由 `CrudSubjectResolver` 从真实登录态解析；默认 Resolver 在无法获得主体时 fail closed。

## `CrudExecutionContext`

`CrudExecutionContext` 是 JDBC 安全、日志和执行阶段使用的只读上下文，包含 operation、scene、阶段、开始时间和扩展属性。它不是登录态，也不代替治理结果。

## `CrudRequestContextHolder`

当前实现使用 ThreadLocal 在同步 Facade 调用范围内传递受信任 attributes。Facade 通过 `withAttributes(...)` 建立作用域，并在调用结束后恢复或清理。

约束：

1. 只允许同步入口适配使用。
2. 不把 HTTP options 直接复制为受信任属性。
3. 嵌套作用域结束后必须恢复外层状态。
4. 异步任务不得依赖线程复用继承上下文。

ThreadLocal 的进一步收窄见 [CRUD 重构路线](../../../evolution/roadmap/crud/clean-refactor-priority.md)。

## 异步任务快照

Import/Export 的 `CrudTaskContextSnapshot` 会保存任务需要的 operation key、主体和治理相关上下文。Worker 应显式读取任务快照，不重新读取创建请求线程的 ThreadLocal。

当前默认 Import/Export 仍以同步小文件闭环为主；完整异步 Worker、快照完整性和重新授权策略属于后续能力，不能描述为已经完成。

## 安全边界

- Subject 是治理输入，ExecutionContext 是执行记录，两者不可混用。
- 请求筛选只能收窄 scope，不能作为主体或授权来源。
- 快照持久化前应明确敏感字段、过期时间和防篡改策略。
- 任何绕过 Gateway 直接调用 Engine 的代码都要自行承担主体、治理和审计责任。
