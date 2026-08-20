# Command 包与职责边界决策

> 状态：Partially Superseded
> 被替代内容：普通 UPDATE Patch 最终命名
> 当前事实：[Command 当前实现](../../../architecture/components/crud/command.md)

## 背景

Command 曾把 Spec、场景 Handler、payload 绑定、默认写入、聚合 Patch 和 delegate 协议混在相邻包中，导致业务扩展需要依赖底层动态载荷细节。

## 仍然有效的决策

Command 按职责组织，而不是按临时实现类堆叠：

```text
command/
  spec            不可变请求合同
  scene           业务场景 Handler 与模板
  patch           普通局部更新视图与 Binder
  aggregate       聚合更新与关系 Patch
  engine          默认执行 SPI
  result          命令结果
```

约束：

1. Spec 不承担 payload 解析。
2. Binder 是动态载荷到强类型视图的统一入口。
3. Scene Handler 不直接依赖 JDBC 实现。
4. delegate 复用默认执行链，但不是业务逻辑的唯一写入方式。
5. 聚合 Patch 与普通单表 Patch 不共享关系职责。

## 已被替代的结论

旧决策曾将 `EntityPatch<T>` 定为普通与聚合更新共同的最终公开名称。该结论已被 [CRUD 强类型边界](../../../architecture/components/crud/typed-boundary.md) 替代：

- 普通单表局部更新目标 API 为 `UpdatePatch<T>`。
- 聚合根更新继续使用 `aggregate.EntityPatch<T>`。
- 当前普通 UPDATE 暂时仍使用 `command.patch.EntityPatch<T>`，直到稳定 API 闭环完成。

后续文档和代码不得再把普通 `EntityPatch<T>` 描述为最终命名。

## 包迁移原则

- 包名迁移按一个可编译闭环完成，不长期保留新旧双入口。
- 先固定合同测试，再移动类型和消费方。
- 删除旧包前检查 Starter、示例、Javadoc 和架构守卫。
- 包结构只是职责表达，不能代替模型所有权和依赖边界。

具体任务由 [CRUD 重构路线](../../roadmap/crud/clean-refactor-priority.md) 跟踪。
