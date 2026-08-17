# Core 架构

Core 文档记录所有组件共享的基础边界、治理、元数据和主体上下文。

## 规范性核心文档

- **P0 / 跨模块裁决基线**：[Metadata Resolution Contract](meta/metadata-resolution-contract.md)
  - 定义元数据来源、属性级优先级、冲突诊断和模块边界，是 Meta、CRUD、DDL、DOC、UI 共同遵循的裁决基线。

## 基础边界与能力

- [组件边界](component-boundaries.md)
- [治理主链](governance/pipeline.md)
- [治理 Core 架构](governance/core-architecture.md)
- [主体上下文](subject/context.md)
- [Meta 注解架构](meta/annotation-architecture.md)
- [Meta 注解分层与适配](meta/layering-summary.md)
- [Meta 解析引擎](meta/parsing-engine.md)
- [Meta 运行时注册表](meta/runtime-registry.md)
- [Meta Runtime Adapters 当前实现](meta/runtime-adapters.md)
