# CRUD 重构路线

> 状态：Remaining
> 最近核验：2026-08-21
> 当前事实：[CRUD 架构文档入口](../../../architecture/components/crud/index.md)

本路线只保留未完成工作和执行顺序。已完成方案已提炼到 Architecture 或 Decision，不再在这里保留全文。

## 执行原则

1. 每次只关闭一个职责边界。
2. 先补合同测试，再做破坏式包名或模块调整。
3. 一个闭环结束时必须可编译、可测试、可回滚。
4. 不长期保留新旧包、模型或 Registry 双入口。
5. 当前事实与目标类型必须在文档中明确区分。

## 当前执行队列

### 1. 异步上下文治理

C7 已完成当前闭环：`CrudRequestContextHolder` 只在同步 Facade 作用域内使用；`CrudTaskContextSnapshot` 显式保存主体、已授予 scope、最终治理 scope 和 `requestId`/`traceId`，并由本地任务服务完成往返持久化；线程池复用测试证明上下文不会隐式泄漏。

后续只保留完整异步 Worker 调度、快照过期和重新授权策略，不在本路线提前创建公共异步 SPI。

## 已完成基线

- JDBC identity 与默认写入链回归。
- Meta-first、CRUD-only、Meta + CRUD 汇聚到唯一 `CrudRuntimeModel`。
- Registry 启动期构建、校验、冻结和关系图预计算。
- Operation Domain / Operation 合法矩阵与 Stats 独立域。

这些事实分别由当前 Architecture 和 [Operation 决策](../../decisions/crud/能力操作重构.md) 维护。

## 完成定义

每项完成必须同时满足：代码和测试通过、旧入口删除、当前架构更新、路线图删除已完成正文。只移动包或新增类型但保留双入口，不算闭环。
