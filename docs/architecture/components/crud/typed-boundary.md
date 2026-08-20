# CRUD 强类型边界

> 性质：Component Contract
> 状态：Target，部分落地
> 最近核验：2026-08-20

本文定义动态 CRUD 入口与强类型业务代码之间的稳定边界。当前代码已经具备统一 Binder、普通 UPDATE 临时 Patch 和强类型 Handler 基类；稳定命名的 `UpdatePatch<T>` 尚未落地。

## 核心原则

```text
HTTP / JSON / Map
  -> Spec 与治理
  -> Payload Binder
  -> DTO / Entity / UpdatePatch<T>
  -> 业务 Handler
  -> Delegate / Engine
```

动态载荷允许存在于框架入口、治理、默认引擎和低阶扩展点；普通业务 Handler 不应重复解析 `Map<String, Object>` 或依赖散落的字段字符串。

## 当前能力

| 场景 | 当前业务视图 | 目标 |
|---|---|---|
| `ACTION` | 明确 DTO | 保持 |
| 普通 CREATE | Entity/DTO Binder 基础能力 | 提供一致模板 |
| 普通 UPDATE | `command.patch.EntityPatch<T>` | 稳定为 `UpdatePatch<T>` |
| 聚合 UPDATE | `aggregate.EntityPatch<T>`、`AggregateRelationPatch<T>` | 保持聚合语义 |
| 默认单表写 | 字段 Map | 继续作为 Engine 内部合同 |
| Batch | 子命令或 item 列表 | 暂不复用单个 Patch |

当前类型与目标类型不能混写成已完成 API。普通 UPDATE 的临时 `EntityPatch<T>` 与聚合 `EntityPatch<T>` 同名，是需要收敛的命名债务。

## 入参选择

| 请求语义 | 推荐类型 |
|---|---|
| 提交、审批、撤销等动作 | 专用 DTO |
| 全量实体式更新 | Entity 或请求 DTO |
| 需要区分字段是否出现的局部更新 | `UpdatePatch<T>` |
| 主表与关系集合的聚合更新 | `aggregate.EntityPatch<T>` |
| 默认引擎内部动态写入 | 规范化字段 Map |

强类型不是“消灭所有 Map”。它要求在进入业务规则前完成一次可测试的绑定，并把动态结构限制在明确边界。

## `UpdatePatch<T>` 语义

局部更新必须区分：字段未出现、字段出现且为 `null`、字段出现且有值。

目标 API 至少提供：

```java
public interface UpdatePatch<T> {
    T getEntity();
    Object getId();
    boolean hasField(String field);
    <V> V get(String field);
    Set<String> getPresentFields();
    Set<String> getPersistableFields();
    Map<String, Object> getValuesForDelegate();
}
```

约束：

1. 所有集合和 Map 对调用方只读。
2. `presentFields` 只包含已识别的请求字段。
3. `persistableFields` 只包含允许进入默认写入链的字段。
4. `hasField()` 表达请求出现性，不等于最终可写性。
5. `getValuesForDelegate()` 是高级扩展逃生口，不是普通业务 API。
6. unknown field 的拒绝、忽略或诊断由统一 Binder 策略处理。

`UpdatePatch<T>` 用于普通单表 PATCH 语义；聚合 `EntityPatch<T>` 继续拥有关系 Patch，不因命名收敛被替换。

## Binder 规则

`CommandPayloadBinder` 是 Object/Map 到业务视图的唯一默认绑定入口。它负责：

- JavaBean 字段绑定和基础类型转换。
- Patch 三态与字段出现性。
- 主键、未知字段和只读集合处理。
- 生成默认引擎可消费的 delegate values。

它不负责业务必填、权限、状态流转、数据范围和事务；这些属于治理或业务 Handler。

聚合 Handler 和 JDBC mapper 后续应复用同一字段识别规则，避免三套反射和字段过滤语义。

## Handler 边界

底层 `CommandUpdateSceneHandler<P, R>` 保留为扩展 SPI。普通局部更新的推荐入口是 `AbstractPatchUpdateSceneHandler<T, R>`；稳定 API 落地后只迁移其回调类型，不改变 routeKey、治理和 delegate 语义。

业务 Handler 应负责：

- 业务校验、状态流转和聚合规则。
- 必要的旧数据加载。
- 选择直接业务 Service 或默认 delegate。

框架基类应负责：

- payload 绑定。
- id、targetFilters、expectedVersion 等框架字段桥接。
- 事务模板和统一结果包装，但只在 Spring 集成层提供事务。

## CREATE、Batch 与关系 Patch

- CREATE 不需要 PATCH 三态时优先绑定 Entity/DTO。
- SAVE_OR_UPDATE 应先明确分支语义，再选择 Entity 或 Patch。
- Batch 每项需要独立 id、字段出现性和错误位置，不能简单包装成一个 `UpdatePatch<T>`。
- 关系子项如果未来支持局部更新，应定义独立的 item Patch，不使用 `AggregateRelationPatch<UpdatePatch<T>>` 隐含复杂语义。

## 验收门禁

稳定 `UpdatePatch<T>` 前后必须覆盖：

1. absent / explicit null / value 三态。
2. unknown、只读、关系和主键字段过滤。
3. id、targetFilters、expectedVersion 的 delegate 桥接。
4. Binder、聚合 Handler 和 JDBC mapper 的共同字段语义。
5. 公开集合不可变。
6. 业务模板不再把裸 Map 作为默认写法。

当前实施顺序见 [CRUD 重构路线](../../../evolution/roadmap/crud/clean-refactor-priority.md)，设计理由见 [强类型 Handler 决策](../../../evolution/decisions/crud/typed-command-handler-conclusions.md)。
