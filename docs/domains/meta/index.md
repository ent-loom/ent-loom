# Meta 领域总览

> 状态：Current

本页是 Meta 的统一导航入口，只负责串联核心契约、实现参考、使用指南和演进路线，不复制各类文档正文。

## 文档分层

| 目的 | 入口 | 内容边界 |
|---|---|---|
| 核心契约 | [元数据约定与裁决契约](../../architecture/core/metadata-resolution-contract.md) | 属性来源、优先级、诊断和 Runtime Model 闭环 |
| 架构总览 | [Meta 分层与运行模型](../../architecture/core/meta/layering-summary.md) | Module-only、Meta-first、模块分层和模型投影 |
| 实现参考 | [Meta 解析引擎](../../architecture/core/meta/parsing-engine.md)、[Runtime Adapters](../../architecture/core/meta/runtime-adapters.md) | 当前解析、装配和已验证路径 |
| 使用指南 | [Meta 指南](../../guides/meta/index.md) | Meta-first 的依赖选择、覆盖规则和使用边界 |
| 后续工作 | [Meta 路线图](../../evolution/roadmap/meta/index.md) | 裁决能力、Convention 和消费者闭环的实施计划 |

## 阅读规则

1. 先以核心契约确认属性来源和模块边界。
2. 再按使用场景选择 Module-only 或 Meta-first。
3. 当前实现与目标能力不一致时，以文档状态和 [Meta 路线图](../../evolution/roadmap/meta/index.md) 为准。

核心契约优先于实现参考；路线图只描述尚未完成的目标，不代表当前已经具备对应能力。
