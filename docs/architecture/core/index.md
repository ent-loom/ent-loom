# Core Contract

> 状态：Current

Core Contract 定义跨模块稳定边界。它们彼此并列，共同受 [系统架构总览](../overview.md) 约束，不由某个组件实现文档覆盖。

## 核心契约

| 契约 | 负责 | 不负责 |
|---|---|---|
| [元数据约定与裁决](metadata-resolution-contract.md) | 属性来源、优先级、诊断、Runtime Model 闭环 | HTTP、事务和具体执行算法 |
| [组件边界与依赖](component-boundaries.md) | Core、Adapter、Starter、业务项目的依赖方向 | 具体 Maven 拆分计划 |
| [治理 Pipeline](governance/pipeline.md) | 主体、权限、范围、有效 Spec 和审计阶段 | 业务权限细节和 SQL 实现 |
| [主体上下文](subject/context.md) | 调用主体和执行上下文 | 登录系统实现 |

## 当前实现参考

- [治理架构](governance/overview.md)
- [治理 Core 实现](governance/core-architecture.md)
- [Meta 分层与运行模型](meta/layering-summary.md)
- [Meta 解析引擎](meta/parsing-engine.md)
- [Meta Runtime Adapters](meta/runtime-adapters.md)
- [CRUD Runtime Registry](../components/crud/runtime-registry.md)

当前实现参考不能修改 Core Contract。实现暂未满足契约时，应在路线图记录差距，而不是降低契约含义。
