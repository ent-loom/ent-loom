# CRUD 重构路线

> 状态：Remaining
> 最近核验：2026-08-20
> 当前事实：[CRUD 架构文档入口](../../../architecture/components/crud/index.md)

本路线只保留未完成工作和执行顺序。已完成方案已提炼到 Architecture 或 Decision，不再在这里保留全文。

## 执行原则

1. 每次只关闭一个职责边界。
2. 先补合同测试，再做破坏式包名或模块调整。
3. 一个闭环结束时必须可编译、可测试、可回滚。
4. 不长期保留新旧包、模型或 Registry 双入口。
5. 当前事实与目标类型必须在文档中明确区分。

## 当前执行队列

### 1. 拆薄 `ent-loom-crud-core`

- 先用依赖图识别稳定边界，再决定 Maven artifact。
- 优先分离 API/contract、runtime、默认实现和可选能力。
- 禁止创建无调用者、无测试的占位模块。
- 保持当前公开 artifact 和 Starter 回归可验证。

### 2. 收窄 annotations 依赖

- annotations 不依赖 Starter 或执行引擎。
- 评估对 Meta Contract 的真实最小依赖。
- 保证 CRUD-only 注解仍可独立生成 Runtime Model。

### 3. 架构守卫

- Core 禁止依赖 Spring、Servlet 和 Starter。
- Starter 主包只能位于 `com.entloom.crud.starter..`。
- Module Core 不依赖 Meta Core。
- Runtime Model 和 Registry 不允许出现第二入口。

### 4. 异步上下文治理

- 将 ThreadLocal 限制在同步入口适配层。
- 异步任务显式快照主体、scope 和审计上下文。
- 禁止依赖线程复用隐式传播权限状态。

## 已完成基线

- JDBC identity 与默认写入链回归。
- Meta-first、CRUD-only、Meta + CRUD 汇聚到唯一 `CrudRuntimeModel`。
- Registry 启动期构建、校验、冻结和关系图预计算。
- Operation Domain / Operation 合法矩阵与 Stats 独立域。

这些事实分别由当前 Architecture 和 [Operation 决策](../../decisions/crud/能力操作重构.md) 维护。

## 完成定义

每项完成必须同时满足：代码和测试通过、旧入口删除、当前架构更新、路线图删除已完成正文。只移动包或新增类型但保留双入口，不算闭环。
