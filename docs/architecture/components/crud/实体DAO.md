# 实体 DAO

> 状态：Proposed<br>
> 最近核验：2026-09-16
> 实施跟踪：[实体 DAO 实施清单](../../../evolution/roadmap/crud/实体DAO实施清单.md)

## 定位

`EntityDao<T, ID>` 是面向单实体、单表操作的基础数据访问合同。它供业务 Service 直接使用，也供通用 CRUD 的 Gateway / Engine 执行链复用。

DAO 不解析调用主体，不判断角色和权限，也不计算租户、组织或业务数据范围。上层先将这些规则解析为 `RowConstraint`，再通过 `EntityDaoFactory` 获得绑定访问范围的 DAO。DAO 负责把已经确定的范围与主键、逻辑删除、期望版本一起原子落实到 SQL。

```mermaid
flowchart TB
    external["HTTP / 外部调用"]
    gateway["Gateway<br/>权限 / 范围解析 / 审计 / 幂等"]
    service["业务 Service / Scene Handler<br/>业务授权 / 规则 / 事务"]
    resolver["Scope Resolver<br/>生成 RowConstraint"]
    factory["EntityDaoFactory.scoped"]
    dao["Scoped EntityDao<br/>单表数据访问"]
    custom["专用 Repository<br/>JOIN / 聚合 / 特殊 SQL"]
    database[("MySQL 8")]

    external --> gateway --> resolver --> factory --> dao --> database
    external --> service --> resolver
    service --> factory
    service --> custom --> database
```

核心边界是：**上层负责范围解析，DAO 负责范围执行**。DAO 不理解“当前用户为什么只能访问这些组织”，但必须保证每次读写都带上已经绑定的行约束。

信任边界明确如下：Gateway、Scene Handler 和业务 Service 属于可信应用层，负责正确完成授权、范围解析和业务规则；DAO 防止 HTTP 参数、DTO、查询条件等外部输入绕过已经确定的范围，但不防止可信业务代码主动构造错误范围或显式选择全量范围。后者属于业务代码审查、模块边界和运维治理责任，不伪装成 DAO 能够解决的安全问题。

## 按访问范围获取 DAO

默认入口不直接按实体类型获取裸 DAO，而是显式绑定访问范围：

```java
public interface EntityDaoFactory {
    <T, ID> EntityDao<T, ID> scoped(
        Class<T> entityType,
        EntityAccessScope scope
    );
}
```

业务调用示例：

```java
RowConstraint constraint = scopeResolver.resolve(subject, Order.class);
EntityAccessScope scope = EntityAccessScope.of(constraint);

EntityDao<Order, Long> orderDao =
    entityDaoFactory.scoped(Order.class, scope);

orderDao.findById(orderId);
orderDao.updateById(orderId, patch);
orderDao.deleteById(orderId);
```

`EntityAccessScope` 是 scoped DAO 的不可变访问上下文：必须包含 `RowConstraint`，并为未来少量项目的水平分片保留可选 `PersistenceRouteHint`。第一阶段只使用 `EntityAccessScope.of(constraint)`，不实现分片路由。

`RowConstraint` 只表达当前实体字段上的行约束，不携带主体、角色、Scene Policy 等治理对象，也不允许包含 SQL 片段。字段和操作符必须经过实体元数据白名单校验，值始终使用参数化绑定。外部请求只能提供用于收窄范围的普通业务条件，不能直接提交、反序列化或替换可执行的 `RowConstraint`；可信应用层负责把授权结果和必要的业务条件解析为最终约束。

约束规则：

- `scope == null` 或 `scope.rowConstraint == null` 视为编程错误并拒绝创建 DAO。
- 需要访问全部数据时必须显式使用 `RowConstraint.unrestricted()`，不以 `null` 表示无限制。
- `RowConstraint.unrestricted()` 是可信应用层可使用的显式能力，不代表 DAO 能阻止业务代码主动放宽范围；常规业务路径仍应通过 scope resolver 获得约束。
- `EntityDao` 实例绑定的约束不可变，可在线程间安全复用；一次调用不能临时移除或放宽它。
- 查询、更新、覆盖和删除始终应用绑定约束与逻辑删除谓词。
- 新增数据必须满足绑定约束；实体缺少范围字段或字段值超出约束时拒绝写入，不静默创建到范围之外。
- 框架默认不注册可被业务代码随意注入的 unscoped DAO；全表维护使用显式的专用 Repository。

## 第一阶段合同

第一阶段只覆盖单实体常用读写闭环，不一次纳入批量、upsert、条件写和键集分页：

```java
public interface EntityDao<T, ID> {
    Optional<T> findById(ID id);

    List<T> findByIds(Collection<ID> ids);

    Map<ID, T> findByIdsAsMap(Collection<ID> ids);

    List<T> list(EntityQuery query);

    EntityPage<T> page(EntityQuery query, PageRequest pageRequest);

    ID insert(T entity);

    int updateById(ID id, T changes);

    int updateById(ID id, T changes, WriteOptions options);

    int updateById(ID id, UpdatePatch<T> patch);

    int updateById(ID id, UpdatePatch<T> patch, WriteOptions options);

    int replaceById(ID id, T entity);

    int replaceById(ID id, T entity, WriteOptions options);

    int deleteById(ID id);

    int deleteById(ID id, WriteOptions options);
}
```

`EntityQuery` 只表达当前实体的过滤和排序；`PageRequest` 表达从 1 开始的页码和每页数量。它们是 DAO contract 的独立模型，不直接使用带 Scene、权限或跨表语义的 `QuerySpec`。

`UpdatePatch<T>` 复用 CRUD 强类型边界中的普通单表 PATCH 模型，能够区分“未提供字段”“显式更新为 `null`”和“更新为具体值”。不再新增同名或近义的 DAO `EntityPatch`，避免与现有聚合 `EntityPatch<T>` 混淆。

`WriteOptions` 只承载单次写入的数据库执行选项。第一阶段至少包含可选的 `expectedVersion`；默认重载等价于未提供期望版本。它不承载主体、权限或数据范围。

## 查询语义

所有查询的有效条件均为：

```text
绑定的 RowConstraint
+ 逻辑未删除谓词
+ 调用方查询条件或主键条件
```

| 方法 | 语义 |
|---|---|
| `findById` | 在有效条件内按主键查询；未命中返回 `Optional.empty()` |
| `findByIds` | 忽略未命中 ID，结果按入参 ID 首次出现顺序排列 |
| `findByIdsAsMap` | 忽略未命中 ID，返回只读映射，迭代顺序与入参 ID 首次出现顺序一致 |
| `list` | 按 `EntityQuery` 查询；必须设置上限，不提供无界 `listAll()` |
| `page` | 返回数据、精确总数、页码和每页数量；数据与计数使用同一有效条件 |

页码查询必须具有确定性排序。调用方未指定排序时按主键升序；指定的排序字段不唯一时，以主键作为最后一个排序字段。

DAO 不通过额外查询向普通读取调用方区分“数据不存在”和“数据存在但不在当前范围”，避免扩大数据可见性。

## 更新与覆盖语义

三个入口表达三种不同意图：

| 方法 | 字段选择 | `null` 语义 | 适用场景 |
|---|---|---|---|
| `updateById(ID, T)` | `changes` 中非 `null` 的可更新持久化属性 | 忽略 | 简单选择性更新 |
| `updateById(ID, UpdatePatch<T>)` | Patch 明确出现的可更新字段 | 显式写入 `null` | PATCH、需要字段三态 |
| `replaceById(ID, T)` | 除主键和框架受控字段外的全部可更新持久化属性 | 写入 `null` | 全量覆盖 |

共同规则：

- 主键只使用方法参数 `id`；实体或 Patch 中的主键若存在，必须与参数一致。
- 主键、逻辑删除、范围约束字段、版本字段等框架受控字段不能作为普通更新字段绕过约束。
- `updateById(ID, T)` 在过滤 `null` 和不可更新字段后没有可写字段时拒绝执行。
- `UpdatePatch<T>` 为空时拒绝执行。
- `replaceById` 的“允许 `null`”表示把 `null` 纳入 SQL 更新，不表示绕过数据库非空约束。
- 所有字段都经过实体元数据白名单校验，值使用参数化绑定。

## 统一写入约束

DAO 在执行按主键更新、覆盖或删除时，将以下部分组合成不可放宽的 `WriteConstraint`：

```text
目标主键
+ scoped DAO 绑定的 RowConstraint
+ 实体元数据声明的逻辑未删除谓词
+ WriteOptions 中可选的 expectedVersion
```

例如：

```sql
UPDATE orders
SET status = ?, version = version + 1
WHERE id = ?
  AND tenant_id = ?
  AND org_id IN (?, ?)
  AND deleted = 0
  AND version = ?;
```

`WriteConstraint` 是 DAO 编译与未命中分类使用的内部统一模型，不等同于治理模型：

- 主键来自方法参数。
- 数据范围来自创建 DAO 时绑定的 `RowConstraint`。
- 逻辑删除来自实体元数据。
- 期望版本来自本次调用的 `WriteOptions`。

实体没有版本元数据时，传入 `expectedVersion` 直接拒绝。更新或覆盖时，版本匹配与版本递增必须在同一条 SQL 中完成；删除时，版本匹配必须进入同一条物理删除或逻辑删除 SQL。不得先查询版本再执行无版本条件的写入。

## 写入未命中分类

更新、覆盖或删除影响 `0` 行时，DAO 复用当前默认引擎的未命中分类能力，根据同一个 `WriteConstraint` 判定：

1. 目标主键不存在或已经逻辑删除。
2. 目标存在，但不满足绑定的 `RowConstraint`。
3. 目标和范围均匹配，但 `expectedVersion` 不匹配。
4. 目标可见且版本匹配，但数据库报告未发生值变化。

分类结果应转换为稳定的“不存在、范围拒绝、乐观锁冲突”异常或正常的未变化结果，不把 MySQL 驱动的影响行数差异直接泄漏给调用方。分类查询必须使用同一连接和参数化条件；对外异常映射仍可将“不存在”和“范围拒绝”统一处理，避免泄露数据存在性。

## 新增语义

- `insert` 返回最终主键；数据库生成主键必须稳定回收。
- 显式主键必须符合实体主键策略和类型。
- 新增字段必须满足实体元数据、数据库约束以及 scoped DAO 绑定的 `RowConstraint`。
- 逻辑删除初始值和版本初始值由持久化映射策略处理，调用方不能借普通字段覆盖。
- DAO 不处理业务必填、状态流转和跨实体规则。

## 后续扩展

以下能力不进入第一阶段最小合同，等主键读写闭环稳定后再逐项加入。

### 非原子批量

批量方法必须在名称中明确 `NonAtomic`，例如：

```java
List<ID> insertBatchNonAtomic(List<T> entities);

int updateBatchByIdNonAtomic(Map<ID, T> entities);

int deleteBatchByIdNonAtomic(Collection<ID> ids);
```

这里的“非原子”表示 DAO 自身不创建事务，也不承诺全部成功或全部失败：

- 实现可以依据数据库限制分块。
- 任一项或任一分块失败时立即抛出异常，但此前操作可能已经提交。
- 调用方需要全量回滚时，必须在 Service 或 Gateway 外层显式开启事务。
- 空集合不访问数据库并返回空结果或 `0`；`null` 入参视为编程错误。
- 方法正常返回表示所有项均已执行，不表示 DAO 曾创建事务。

如果未来提供 DAO 自己保证原子性的批量入口，应使用不同名称和明确的事务合同，不能静默改变上述语义。

### 主键 upsert

`insertOrUpdateById` 只允许把主键冲突视为同一实体，其他唯一键冲突必须抛出约束异常。MySQL `ON DUPLICATE KEY UPDATE` 会响应任意唯一键冲突，不能直接满足该合同；实现算法和并发行为经过 MySQL 8 验收前，不进入第一阶段接口。

### 条件写

后续可增加：

```java
int updateByCondition(EntityCondition condition, UpdatePatch<T> patch);

int deleteByCondition(EntityCondition condition);
```

调用方条件始终与 scoped DAO 的 `RowConstraint`、逻辑删除条件共同生效。空条件、规范化后恒真的条件或空 Patch 直接拒绝；DAO 不提供 `updateAll`、`deleteAll`。

### 键集分页

键集分页稳定后再增加 `keysetPage`。游标由 DAO 生成和解析，至少绑定实体、完整查询指纹、排序字段、排序方向、末行排序值和元数据版本，并进行完整性校验。游标与当前查询不兼容时拒绝执行，不静默回到第一页。

## 职责边界

| Entity DAO 负责 | Entity DAO 不负责 |
|---|---|
| 执行已绑定的 `RowConstraint` | 从主体、角色计算数据范围 |
| 实体元数据到表、列的映射 | 用户、角色与权限判断 |
| 单表 SQL 编译和参数绑定 | Scene Policy 与业务状态校验 |
| 主键、范围、逻辑删除和版本的原子写入谓词 | 幂等与治理审计 |
| 数据库生成主键回收和异常转换 | 跨实体事务编排 |
| 逻辑删除、版本等持久化映射策略 | JOIN、聚合、报表和特殊 SQL |
| 参数化、标识符白名单等 SQL 安全 | 默认创建事务 |

所谓“不处理治理”，是指 DAO 不拥有治理规则和解析过程，不表示 DAO 可以忽略上层已经解析好的行约束。范围执行、逻辑删除、乐观锁、字段映射和 SQL 安全都属于 DAO 必须守住的数据库边界。

DAO 的安全保证从“收到最终 `EntityAccessScope`”开始：它保证绑定范围不会被查询条件或写入参数放宽，并保证范围进入实际 SQL；它不验证可信应用层为何生成该范围，也不承担防止业务代码主动传入错误范围的职责。

## 调用边界

- Controller 不直接暴露 DAO；对外通用 CRUD 仍先经过 Gateway 治理。
- HTTP 参数、请求 DTO 和其他不可信输入不能直接映射为 `EntityAccessScope` 或 `RowConstraint`。
- Gateway 解析治理范围并创建 scoped DAO，不能先做范围查询再调用不带范围的写入。
- 业务 Service 作为可信边界，可以使用自身的 scope resolver 或显式范围创建 scoped DAO，并负责业务授权、规则和事务编排。
- 复杂查询和跨表持久化使用专用 Repository；Repository 同样不能绕过必要的数据范围。
- 常规业务不应通过 `RowConstraint.unrestricted()` 构造捷径；全表维护优先使用职责明确的专用 Repository 和事务。该约束由应用架构与代码审查保证，不由 DAO 冒充权限系统强制判断。

## 水平分片演进边界

水平分片不改变 `EntityDao` 的 CRUD 方法，而是在创建 scoped DAO 时确定逻辑路由。`RowConstraint` 表达“允许访问哪些行”，`PersistenceRouteHint` 表达“本次访问归属哪个逻辑路由键”；两者相关但不合并，路由器不从任意条件树中反向猜测分片键。

```java
EntityAccessScope scope = EntityAccessScope.builder()
    .rowConstraint(rowConstraint)
    .routeHint(PersistenceRouteHint.by("tenantId", tenantId))
    .build();
```

`PersistenceRouteHint` 只表达单个实体字段的等值逻辑路由，不向业务层暴露物理库、物理表、`DataSource` 或 `JdbcTemplate`。出现第一个真实分片项目前，不定义 `ShardId`、副本角色、物理表等未经验证的通用模型，也不增加分片 Maven 模块。

分片实体默认遵守：

- 缺少路由键直接拒绝，不静默广播。
- 一次 DAO 调用只允许命中一个分片；`findByIds` 和批量写同样不跨分片。
- `insert` 的实体分片字段、`RowConstraint` 和 `PersistenceRouteHint` 必须一致。
- 同一事务不允许切换分片；路由必须在首次获取连接前确定。
- 逻辑删除、乐观锁与写入未命中分类必须在同一分片、同一连接内完成。
- 跨分片 JOIN、事务、精确分页、全局排序、聚合和导出不进入通用 DAO，改用专用读模型、Search 或 OLAP。
- 分片后主键需保证全局唯一；跨分片业务唯一性不依赖单库唯一索引。

实现取舍：

| 场景 | 建议 |
|---|---|
| 仅按 `tenantId` 等稳定归属键选择数据源，严格单分片 | 实现轻量 JDBC 路由 |
| 同时分库分表，需要逻辑表 SQL 改写、读写分离等标准能力 | 选用 ShardingSphere-JDBC |
| 大量跨分片查询、排序、分页或聚合 | 调整 OLTP 边界，不在 DAO 或分片中间件中硬扛 |

轻量自研只包含“路由键校验、固定逻辑分片计算、路由表到数据源映射、配置热更新和事务路由一致性”；不自研 SQL 解析、表名改写、广播执行、结果归并或分布式事务。ShardingSphere-JDBC 作为可选 `DataSource` 实现时，DAO 仍先校验逻辑路由键并默认禁止广播，不因中间件“可以执行”而放宽 OLTP 边界。

## 模块建议

DAO 合同不依赖 Gateway、具体治理模型或 Spring：

```mermaid
flowchart TB
    contract["DAO contract<br/>Factory / EntityDao / AccessScope"]
    jdbc["JDBC implementation<br/>JdbcEntityDao"]
    starter["Spring Boot Starter<br/>Factory / Bean 装配"]
    gateway["Gateway / Engine"]
    business["业务 Service"]

    jdbc --> contract
    starter --> jdbc
    gateway --> contract
    business --> contract
```

第一阶段放在现有 `crud-core` 与 `crud-engine-jdbc` 的清晰包边界内：前者放置 `dao` 合同，后者放置 `dao` JDBC 实现及谓词、变更 SQL、写入未命中分类部件。逻辑删除和乐观锁属于 DAO 的持久化一致性，不是额外拆分 `crud-core-governance` 或 `crud-core-dao` 的理由。

第一阶段不新增占位 Maven 模块。只有出现第二种持久化实现、业务项目需要独立依赖 DAO，或 DAO 已形成独立发布和兼容性验证需求后，再提取稳定的 `ent-loom-crud-dao-api` 和 `ent-loom-crud-dao-jdbc`。轻量路由或 ShardingSphere 适配模块同样等真实项目需求出现后再建立，不让普通单库项目承担分片依赖。

## 落地顺序

1. 定义 `RowConstraint`、`EntityAccessScope`、`WriteOptions`、`EntityDaoFactory` 和第一阶段 `EntityDao` 合同；`PersistenceRouteHint` 只作可选逻辑路由语义，不实现分片。
2. 从现有查询编译器和 `JdbcWritePredicateBuilder` 提取行约束、逻辑删除、版本谓词的复用部件。
3. 将 `JdbcWriteMissClassifier` 调整为消费统一的 `WriteConstraint`，保留当前未命中分类能力。
4. 实现 scoped `JdbcEntityDao` 的主键查询、新增、选择性更新、Patch、覆盖和删除。
5. 让默认 Gateway / Engine 改为创建 scoped DAO，并通过等价测试确认治理范围、逻辑删除和乐观锁没有退化。
6. 增加 H2 行为测试与 MySQL 8 方言验收，再提供 Spring Bean 装配。
7. 按真实调用需求逐项增加非原子批量、条件写、主键 upsert 和键集分页。
8. 首个真实分片项目出现后，先验证单分片路由闭环；只有需要 SQL 改写时才引入 ShardingSphere-JDBC 适配。

当前仓库尚未提供 `EntityDao` 实现，后续以本文作为 DAO 落地边界。
