# 治理主链 (Governance Pipeline)

`ent-loom` 所有的核心操作（Query, Command, Stats, Import, Export）都必须经过统一的治理主链。这确保了安全性、合规性和一致性。

## 1. 核心契约：`CrudGovernanceService`

该服务定义了治理的入口。每个一级能力都有对应的 `govern` 方法。

```java
public interface CrudGovernanceService {
    <R> CrudGovernanceResult<QueryExecutionSpec<R>> governQuery(QuerySpec<R> spec);
    <P> CrudGovernanceResult<CommandExecutionSpec<P>> governCommand(CommandSpec<P> spec);
    <S extends BaseSpec & GovernableSpec<S>> CrudGovernanceResult<S> governStats(S spec);
    // ... import / export
}
```

## 2. 治理七阶段

在 `DefaultCrudGovernanceService` 中，治理流程被严格划分为七个阶段（Stage）：

### 2.1 SUBJECT (主体识别)
*   **职责**: 通过 `CrudSubjectResolver` 解析当前请求的调用者身份（Subject）。
*   **产出**: 包含 `subjectId`、`tenantId`、`orgId` 的 `SubjectContext`。

### 2.2 ATTRIBUTES (属性解析)
*   **职责**: 解析请求中的扩展属性，为后续校验提供上下文。

### 2.3 VALIDATE (规格校验)
*   **职责**: 检查 Spec 格式是否合法。例如：分页参数是否越界、排序字段是否在白名单内。

### 2.4 RESOURCE (资源识别)
*   **职责**: 结合 `EntityMeta` 确定本次操作的目标资源（Resource）和动作（Action）。
*   **产出**: `CrudResourceAction`。

### 2.5 PERMISSION (权限判定)
*   **职责**: 调用 `CrudPermissionService` 判断当前 Subject 是否有权对该 Resource 执行该 Action。
*   **逻辑**: 默认采用 `Fail-Closed`（默认拒绝）策略。

### 2.6 SCOPE (范围解析)
*   **职责**: 调用 `CrudDataScopeResolver` 解析数据范围。
*   **产出**: `CrudDataScope`。例如：只能查看本部门的数据。
*   **后续**: 该范围会通过 `CrudScopeIntersectionService` 与业务强制约束取交集。

### 2.7 ENRICH (规格增强)
*   **职责**: 将治理产生的所有元数据（Subject, Decision, Scope）注入到原始 Spec 中，生成最终的 `ExecutionSpec`。

## 3. 治理结果：`CrudGovernanceResult`

治理结果包含了执行所需的所有上下文：
- **`effectiveSpec`**: 增强后的执行规格，后续引擎直接使用。
- **`accessDecision`**: 访问决策（ALLOW / DENY / MASK / FILTER）。
- **`governanceScope`**: 最终生效的数据范围约束。

## 4. 审计记录

主链在完成后会触发 `recordAllow` 或 `recordExecutionFailure`，通过 `CrudGovernanceAuditRecorder` 记录审计日志。审计信息包含了请求、主体、资源、决策以及最终执行结果，是系统合规性的重要保障。
