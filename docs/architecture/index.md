# 架构文档

> 状态：Current

架构文档包含两部分：跨模块 Core Contract，以及各组件经过代码核验的当前实现。目标方案、取舍记录和实施计划统一放在 [evolution](../evolution/index.md)。

## 阅读顺序

1. [系统架构总览](overview.md)
2. [Core Contract](core/index.md)
3. [组件架构](components/index.md)

## 边界

- `core/` 定义跨模块不变量，不依赖某个 Starter 或业务项目。
- `components/` 定义组件运行时模型、执行链和公开边界。
- 模块内部算法、包布局和维护细节放在模块自己的 `docs/implementation/`。
- 尚未落地的内容不得写成当前能力。
