# CRUD 治理 Pipeline

> 性质：Core Contract
> 状态：Current
> 最近核验：2026-08-20

通过内置 Query、Command、Stats、Import、Export Gateway 的请求必须先完成治理，再进入 Scene Handler、Engine 或 Task/File 操作。直接调用底层 Engine 不自动获得这项保证。

## 七个阶段

```mermaid
flowchart LR
    subject["1 SUBJECT"] --> attributes["2 ATTRIBUTES"]
    attributes --> validate["3 VALIDATE"]
    validate --> resource["4 RESOURCE"]
    resource --> permission["5 PERMISSION"]
    permission --> scope["6 SCOPE"]
    scope --> enrich["7 ENRICH"]
```

| 阶段 | 输入与产出 |
|---|---|
| SUBJECT | 解析并规范化 `SubjectContext`；缺少 subjectId 默认拒绝 |
| ATTRIBUTES | 移除调用方保留键，合并受信任 Contributor 属性 |
| VALIDATE | 校验并规范化 Spec |
| RESOURCE | 从 CRUD Registry 解析资源与 operation key |
| PERMISSION | 生成访问决策；DENY 立即失败 |
| SCOPE | 解析授权范围，与业务 Contributor 约束取交集 |
| ENRICH | 构造治理后的有效 Spec 与治理结果 |

治理不执行 SQL，也不选择业务 Handler。

## Fail-closed 规则

- 默认 Subject Resolver 在没有真实身份来源时拒绝。
- 无匹配权限、显式 DENY 或非法 operation 拒绝。
- 非全量 scope 没有有效维度时拒绝。
- 业务 scope 只能收窄授权范围，不能扩大。
- 请求 attributes 不能注入框架治理保留键。

## 结果与执行

治理成功后返回 `CrudGovernanceResult`，包括主体、资源动作、决策、授权范围、最终范围和有效 Spec。Gateway 随后执行：

```text
governed Spec -> Scene Handler or Engine/Task Service -> audit -> result
```

ACTION 等高风险 scene 的额外 Scene Policy 仍处于实施阶段，见 [Scene Policy 计划](../../../evolution/roadmap/crud/scene-policy-governance.md)。

## 审计时点

- 治理拒绝：治理服务记录 deny。
- 执行成功：Execution Pipeline 记录 allow/success。
- 执行失败：Execution Pipeline 记录 execution failure。

审计至少关联 request/trace、subject、resource、operation、scene、decision、scope、结果和耗时。

## 扩展边界

业务可以实现 Subject Resolver、Permission Service、Data Scope Resolver 和 Scope Contributor。扩展实现仍必须返回统一模型，不得绕开阶段顺序或从请求筛选重新构造授权范围。

当前默认实现细节见 [治理 Core 架构](core-architecture.md)。
