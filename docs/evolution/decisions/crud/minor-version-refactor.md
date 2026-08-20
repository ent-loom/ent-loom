# CRUD 小版本重构取舍

> 状态：Superseded as plan
> 当前路线：[CRUD 重构路线](../../roadmap/crud/clean-refactor-priority.md)

本文原先同时承担重构建议、实施顺序和验收清单。相关任务已由当前路线图接管；这里仅保留仍然有效的取舍。

## 保留的原则

1. 框架尚在早期阶段时，可以接受有测试保护的内部破坏式重构。
2. 外部 HTTP、JSON、配置和 Starter 装配必须先冻结合同，再修改内部包结构。
3. Spec 对调用方不可变，治理后产生新的有效 Spec。
4. Runtime Model 和 Registry 必须唯一、启动期校验并冻结。
5. Query、Command、Stats、Import、Export 使用统一 operation key，但保留各自执行边界。
6. 默认 Engine 只承担安全、明确的通用能力，复杂业务进入 Scene Handler。
7. 不同时修改公开契约、Core 拆分和 Starter 迁移，保持小闭环可回滚。

## 已完成

- 不可变 Spec 与结构化 operation key。
- 唯一 CRUD Runtime Model 与冻结 Registry。
- Stats 独立操作域。
- Import/Export 独立 Engine 和 Task/File 主链。
- JDBC 默认写入链回归。

## 不再由本文维护

`UpdatePatch<T>`、Starter 包名、Core 拆分、注解依赖、架构守卫和异步上下文均以当前路线图为准。本 Decision 不作为任务清单使用。
