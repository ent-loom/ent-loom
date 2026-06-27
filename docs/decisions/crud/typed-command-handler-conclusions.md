# 强类型命令 Handler 重构定稿记录

本文记录围绕 `BusMoralRecordBatchUpdateFullHandler` 的一轮追问、取舍、定稿结构和后续推广方向。目标是给后续改造 `CREATE`、`DELETE`、`SAVE_OR_UPDATE`、批量命令等 handler 时提供同一套判断标准。

本文不是重新定义底层 CRUD 引擎，而是定义业务 handler 的推荐写法：业务层尽量面向对象、使用实体或 DTO 承载语义；`Map<String, Object>` 保留在框架入口、治理、默认引擎和少量逃生口中。

## 需求脉络

本轮讨论中的核心诉求按顺序可以概括为：

| 追问点 | 最终理解 |
|---|---|
| `@Transactional` 方法必须可覆写 | 事务边界不应要求每个业务 handler 重复 override `handle(...)`，应由 Spring 父类统一提供。 |
| `BusMoralRecordBatchUpdateFullHandler` 强类型不彻底 | 业务层不应围绕每个字段写 `Map` 读取逻辑，应优先使用实体或明确 DTO。 |
| 不追求 0 Map，但常规增删改应强类型 | `Map` 可以存在于框架边界和默认引擎，常规业务 handler 应有强类型模板。 |
| 实体可作为强类型容器 | 对于 `BusMoralRecordBatch` 这种主实体，`@TableField(exist = false)` 的子集合可以作为请求聚合容器。 |
| 子表更新不关心具体字段名 | 业务希望表达“按实体集合同步子表”，而不是逐字段处理 `requestedMediaUrls`。 |
| 基础增删改前后可插入定制逻辑 | 推荐用模板方法：父类处理绑定、事务、返回结构；业务只实现 `create/update/delete` 及少量前后置校验。 |
| 核心是面向对象，不要面向字段逐个硬写 | 默认业务模型应是实体、DTO、值对象、集合差异同步，而不是散落字符串字段。 |
| 避免过度设计 | 只把重复且稳定的东西上提到父类；业务差异、权限和聚合规则仍留在业务类或业务服务。 |
| 增删改是否走 MyBatis-Plus | 业务 handler 内的主表、子表实体化增删改推荐使用已有 MyBatis-Plus service。默认 JDBC engine 继续作为框架默认能力。 |
| `invokeDelegateUpdate` 是否改成 service 版本 | 对该业务类，定稿为直接使用 MyBatis-Plus service，不再把普通业务更新绕回 delegate。 |
| `UpdatePatch` 是否过度封装 | 对“全量实体式更新 + 子表同步”的常规场景，`UpdatePatch` 确实偏重；业务可直接接收实体。Patch 留给真正局部更新、需要区分字段是否出现的场景。 |
| 返回结构是否强类型 | 常规增删改建议统一返回 `CommandResult<RowsResult>`，业务实现只返回 `RowsResult`。 |
| `additionalEntityFields` 是否可缺省 | 可缺省。实体绑定器应直接按 Java 字段绑定，包括 `@TableField(exist = false)` 的业务容器字段。 |
| 构造器能否简化 | 业务类使用 `@RequiredArgsConstructor` + `final` 依赖。父类提供 no-arg 构造和 Spring 注入 `EntityMetaRegistry`，避免业务构造器显式 `super(...)`。 |
| 权限治理注解放实体还是 handler | 资源级静态元数据放实体；场景级、动作级、上下文相关权限放 handler 或业务服务。 |
| 父类递归层级是否过多 | 当前三层有清晰职责边界，建议保留，不继续合并。 |

## 一句话定稿

业务常规更新 handler 的推荐形态是：

```java
@Component
@RequiredArgsConstructor
public class XxxUpdateFullHandler extends RowsUpdateHandler<XxxEntity> {
    private final XxxService xxxService;
    private final XxxChildService childService;

    @Override
    protected String scene() {
        return XxxSceneCodes.XxxEntity.Command.UPDATE_FULL;
    }

    @Override
    protected RowsResult update(XxxEntity requested) {
        // 1. 基础校验
        // 2. 加载旧数据
        // 3. 权限/状态/业务规则校验
        // 4. MyBatis-Plus 更新主表
        // 5. 同步子表集合
        // 6. 返回 RowsResult
    }
}
```

业务类不再关心：

- `CommandSpec<Object>` 到实体的绑定。
- `CommandResult<RowsResult>` 的包装。
- `@Transactional` 的重复声明。
- `EntityMetaRegistry` 的构造器传递。
- `Map<String, Object>` 的字段读取。

## 定稿父类结构

当前推荐保留三层：

```text
RowsUpdateHandler<T>
  -> TransactionalEntityUpdateHandler<T, CommandResult<RowsResult>>
      -> AbstractEntityUpdateHandler<T, R>
```

三层职责如下：

| 类 | 所在层 | 职责 |
|---|---|---|
| `AbstractEntityUpdateHandler<T, R>` | core | 根据 `CommandSpec<Object>` 绑定实体、生成 route key、调用 `handleEntity(T)`。不依赖 Spring。 |
| `TransactionalEntityUpdateHandler<T, R>` | starter | 统一 `@Transactional(rollbackFor = Exception.class)`，并通过 Spring 注入 `EntityMetaRegistry`。 |
| `RowsUpdateHandler<T>` | starter | 把业务返回的 `RowsResult` 统一包装成 `CommandResult<RowsResult>`，业务只实现 `update(T)`。 |

不建议继续合并的原因：

- `AbstractEntityUpdateHandler` 必须保持 core 纯净，不能依赖 Spring 事务。
- `TransactionalEntityUpdateHandler` 是“实体 + 事务 + 任意返回”的复用点，未来非 `RowsResult` 的更新场景仍可使用。
- `RowsUpdateHandler` 是业务最常用的窄模板，负责把返回结构固定下来。
- 业务开发只看到 `RowsUpdateHandler<T>`，内部三层对业务心智负担很低。

## 实体作为强类型容器

对于常规主子表写入，推荐让主实体承载业务请求聚合：

```java
public class BusMoralRecordBatch extends BaseAuditEntity {
    @TableField(exist = false)
    private List<BusMoralRecordLine> busMoralRecordLineList;

    @TableField(exist = false)
    private List<BusMoralRecordMedia> busMoralRecordMediaList;
}
```

业务 handler 直接使用：

```java
protected RowsResult update(BusMoralRecordBatch requested) {
    List<BusMoralRecordMedia> mediaList = requested.getBusMoralRecordMediaList();
}
```

这样比 `payload.get("mediaUrls")` 更直观：

- 字段名可被 IDE、编译器和 Lombok getter/setter 保护。
- 子项结构可以自然扩展，不需要每次新增字段都改 Map 转换代码。
- 业务代码表达的是“同步图片子项集合”，不是“处理某几个字符串字段”。

注意：实体作为请求容器适合“这个请求天然就是这个实体的聚合变更”。如果请求语义不是实体本身，例如提交、撤销、审批、导入、批量动作，应使用明确 DTO，而不是强行塞进实体。

## UpdatePatch 的位置

`UpdatePatch<T>` 不是废弃方向，但不适合作为所有更新业务的默认入口。

适合 `UpdatePatch<T>` 的场景：

- 局部更新，需要区分“字段没传”和“字段传了 null”。
- 默认引擎需要知道哪些字段要写库。
- 业务规则关心字段是否出现在请求里。
- 通用 PATCH API，而不是具体业务的 full update。

适合实体 `T` 的场景：

- 全量或准全量业务更新。
- 主子表集合同步。
- 业务只关心最终对象状态，不关心每个字段是否出现。
- 代码主要由校验、加载旧数据、权限判断、实体保存、子表同步组成。

本轮 `BusMoralRecordBatchUpdateFullHandler` 定稿属于第二类，因此使用实体作为业务入口。

## 子表同步模式

主子表同步不要写成逐字段定制，而应写成集合差异同步：

```text
requested children
existing children
business key / id
  -> toInsert
  -> toUpdate
  -> deleteIds
```

推荐步骤：

1. 从主实体读取请求子集合。
2. 查询数据库旧子集合。
3. 选择稳定匹配键：优先 id；无 id 时使用业务唯一键，例如 mediaUrl、positionId、studentId。
4. 把旧集合和新集合转成 Map。
5. 遍历请求集合：旧数据不存在则 insert，存在则补 id 后 update。
6. 遍历旧集合：请求中不存在则 delete。
7. 使用 MyBatis-Plus service 批量 `insertBatch`、`updateBatchById`、`deleteBatchIds`。

这类逻辑可以沉淀为业务工具，但不要过早做成强框架 API。只有当多个业务类出现相同模式、相同差异策略、相同错误处理时，再抽 `EntitySyncUtil` 或领域内 sync service。

## 返回结构

常规写操作建议统一：

```text
业务方法返回 RowsResult
父类包装 CommandResult<RowsResult>
```

业务代码：

```java
@Override
protected RowsResult update(BusMoralRecordBatch requested) {
    if (!service.updateById(requested)) {
        throw new RuntimeException("更新失败");
    }
    return RowsResult.of(1);
}
```

父类负责：

```java
CommandResult.success(update(entity));
```

这样做的好处：

- HTTP / Facade 层返回结构固定。
- 业务层不用重复 `CommandResult.success(...)`。
- 业务含义仍然清晰：本次操作影响了多少行。

如果某个业务操作天然需要返回业务对象、详情 DTO、异步任务 ID，则不应继承 `RowsUpdateHandler<T>`，而应继承更通用的事务实体模板或使用 action DTO handler。

## 事务边界

事务边界放在 Spring 父类：

```java
public abstract class TransactionalEntityUpdateHandler<T, R>
    extends AbstractEntityUpdateHandler<T, R> {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
        return super.handle(spec, delegate);
    }
}
```

业务类不重复 override `handle(...)`。

原因：

- 避免每个业务 handler 复制相同事务代码。
- 避免业务 handler 写错事务注解。
- `@Transactional` 标注在 public、非 final、可被 Spring 代理的方法上。
- 业务只实现 protected 模板方法，事务已经在外层 `handle(...)` 打开。

如果未来确实需要定制事务传播级别，可新增更具体的父类或允许少量业务类直接继承 `TransactionalEntityUpdateHandler<T, R>` 并覆盖策略；不要因为少量特例让所有业务类承担事务样板代码。

## 构造器与依赖注入

业务类推荐：

```java
@Component
@RequiredArgsConstructor
public class XxxUpdateFullHandler extends RowsUpdateHandler<XxxEntity> {
    private final XxxService xxxService;
    private final XxxChildService childService;
}
```

不推荐：

```java
@Autowired
private XxxService xxxService;
```

也不推荐业务类显式传递：

```java
super(entityMetaRegistry, XxxEntity.class, scene);
```

定稿方案是父类支持：

- no-arg 构造。
- 根据泛型推断 `entityType`。
- 由业务类 override `scene()`。
- 由 Spring 父类注入 `EntityMetaRegistry`。

因此业务类可以使用 Lombok `@RequiredArgsConstructor` + `final` 依赖，同时没有 `super(...)` 噪音。

## 权限治理放哪里

推荐分层：

| 权限信息 | 推荐位置 | 说明 |
|---|---|---|
| 资源级静态信息 | 实体注解 | 例如资源归属、基础 action、默认 access entry。 |
| 通用 CRUD 权限 | 框架治理层 | 例如 create/update/delete 是否允许、字段白名单、数据范围。 |
| 场景级业务权限 | handler 或业务 service | 例如“老师只能编辑自己或任教班级内点评”。 |
| 复杂策略匹配 | 独立业务 service | 例如按岗位规则、优先级、默认规则解析权限范围。 |

不要把所有权限都注解化。注解适合静态声明，不适合表达依赖当前用户、当前数据状态、旧数据加载结果、岗位规则优先级的业务判断。

## 推广到 CREATE

后续如果要同理改造 `CREATE`，建议沿用同样三层结构：

```text
RowsCreateHandler<T>
  -> TransactionalEntityCreateHandler<T, CommandResult<RowsResult>>
      -> AbstractEntityCreateHandler<T, R>
```

业务形态：

```java
@Component
@RequiredArgsConstructor
public class XxxCreateFullHandler extends RowsCreateHandler<XxxEntity> {
    private final XxxService xxxService;
    private final XxxChildService childService;

    @Override
    protected String scene() {
        return XxxSceneCodes.XxxEntity.Command.CREATE_FULL;
    }

    @Override
    protected RowsResult create(XxxEntity requested) {
        // 1. 校验
        // 2. 补当前用户、学校、默认状态、创建时间
        // 3. insert 主表
        // 4. 回填主表 id 到子项
        // 5. insert 子表
        return RowsResult.of(1);
    }
}
```

与 UPDATE 的主要差异：

- CREATE 通常不要求 id。
- 需要补默认值、创建人、学校、状态。
- 子表通常只有 insert，没有 delete 差异同步。
- 如果前端可能传 id，应明确是否拒绝或作为业务幂等键处理。

不要一开始就抽过多 `BeforeCreateHook`、`AfterCreateHook`。业务方法内 5 到 8 步清晰流程足够，重复出现三次以上再抽业务 support。

## 推广到 DELETE

DELETE 是否强类型要更谨慎，因为删除操作经常只需要 id 或 ids。

可选方案：

```text
RowsDeleteHandler<T>
  -> TransactionalEntityDeleteHandler<T, CommandResult<RowsResult>>
      -> AbstractEntityDeleteHandler<T, R>
```

业务形态可以是：

```java
protected RowsResult delete(T requested)
```

但如果大量 delete 只需要 id，使用实体可能反而啰嗦。可以考虑：

```java
protected RowsResult deleteById(Long id)
```

或：

```java
protected RowsResult delete(DeleteRequest<T> request)
```

定稿建议：

- 单 id 删除：优先简单 id 合同。
- 删除前需要加载实体、校验状态、同步删除子表：可以绑定实体或封装 delete request。
- 批量删除：使用 `ids` 合同，不强行把每个 id 包成实体。
- 逻辑删除和物理删除必须在 handler 名称或 scene 中清楚表达。

## 推广到 SAVE_OR_UPDATE

`SAVE_OR_UPDATE` 不建议过早抽成复杂模板。

原因：

- 有的业务按 id 判断。
- 有的业务按业务唯一键判断。
- 有的业务 create/update 前后置逻辑完全不同。
- 子表同步在 create 和 update 下经常不同。

推荐等 `RowsCreateHandler<T>` 和 `RowsUpdateHandler<T>` 稳定后，再考虑：

```text
RowsSaveOrUpdateHandler<T>
```

业务方法可以拆成：

```java
protected boolean isCreate(T requested)
protected RowsResult create(T requested)
protected RowsResult update(T requested)
```

如果只有个别场景需要 upsert，直接写 action handler 或普通业务 service 更清晰。

## 推广到批量操作

批量操作不要简单理解成 `List<T>` 循环。

需要先确认：

- 是否要求单事务全部成功。
- 是否允许部分成功。
- 返回结构是否只是总 rows，还是需要每项结果。
- 批量项之间是否存在依赖。
- 子表集合是否也要批量同步。

简单批量可设计：

```java
protected RowsResult createBatch(List<T> requestedList)
protected RowsResult updateBatch(List<T> requestedList)
```

复杂批量建议使用明确 DTO：

```java
BatchCreateXxxCommand
BatchUpdateXxxCommand
BatchItemResult
```

不要为了“所有 command 都长一样”而牺牲批量错误语义。

## ACTION 与 CRUD 的边界

不是所有写操作都要纳入 CRUD handler。

适合 CRUD handler：

- 新增实体。
- 更新实体。
- 删除实体。
- 保存或更新实体。
- 主子表 full create / full update。

适合 ACTION handler：

- 提交、撤销、审批、发布、上架、下架。
- 生成、导入、导出、重新计算。
- 状态机动作。
- 输入和输出都不是单纯实体的业务行为。

ACTION 推荐 DTO：

```java
SubmitRequest -> SubmitResponse
RevokeRequest -> RevokeResponse
```

不要为了“强类型”把动作请求硬塞进实体。强类型的目标是表达清晰，不是所有地方都用实体。

## 命名建议

父类命名：

| 场景 | 推荐名 |
|---|---|
| core 实体更新 | `AbstractEntityUpdateHandler<T, R>` |
| Spring 事务实体更新 | `TransactionalEntityUpdateHandler<T, R>` |
| 返回 rows 的业务更新 | `RowsUpdateHandler<T>` |
| core 实体创建 | `AbstractEntityCreateHandler<T, R>` |
| Spring 事务实体创建 | `TransactionalEntityCreateHandler<T, R>` |
| 返回 rows 的业务创建 | `RowsCreateHandler<T>` |
| 返回 rows 的业务删除 | `RowsDeleteHandler<T>` |

业务类命名：

| 场景 | 推荐名 |
|---|---|
| 主子表完整新增 | `XxxCreateFullHandler` |
| 主子表完整更新 | `XxxUpdateFullHandler` |
| 删除并同步清理 | `XxxDeleteFullHandler` |
| 普通字段更新 | `XxxUpdateHandler` |
| 业务动作 | `XxxSubmitActionHandler`、`XxxRevokeActionHandler` |

## 不要过度设计的边界

以下能力暂时不要急着抽：

- 通用 before/after hook 链。
- 所有子表同步的超级泛型工具。
- 所有 CRUD 操作的一套万能父类。
- 复杂事务传播策略 DSL。
- 把权限规则全部注解化。
- 为了消灭 `Map` 改造框架内部默认引擎。

可以抽的前提：

- 至少 2 到 3 个业务类出现相同结构。
- 抽象后业务代码明显变短且语义更清晰。
- 不需要业务为了适配抽象而写更多配置。
- 错误语义、事务语义、返回语义可以统一。

## 后续改造清单

改造其他 handler 时可以按这个顺序判断：

1. 这个操作是 CRUD 还是 ACTION。
2. 入参天然是实体、实体聚合，还是业务 DTO。
3. 返回是否可以统一为 `RowsResult`。
4. 是否需要区分 PATCH 字段出现性。如果需要，用 Patch；否则用实体。
5. 是否需要事务。常规写操作默认需要。
6. 主表写入是否可直接走 MyBatis-Plus service。
7. 子表是简单 insert、差异同步，还是复杂业务动作。
8. 权限是静态资源权限，还是场景级业务权限。
9. 是否已有父类模板可用，没有则先写清楚业务类，等重复出现再抽父类。

## 当前最佳实践结论

最终结论：

- 业务 handler 里不追求 0 `Map`，但常规增删改不应面向字段字符串编程。
- `BusMoralRecordBatchUpdateFullHandler` 这类常规主子表更新，应使用实体作为强类型容器。
- `RowsUpdateHandler<T>` 是业务层推荐入口，足够短，也足够稳定。
- 当前三层父类结构有必要：core、Spring transaction、rows result 三个职责不能混在一起。
- `UpdatePatch<T>` 保留给真正 PATCH 场景，不作为 full update 的默认入口。
- 主表和子表实体化写入优先使用 MyBatis-Plus service。
- 权限静态元数据放实体，场景级权限判断放 handler 或业务 service。
- 后续 create/delete 等 handler 可以按同样三层结构推导，但应按真实重复程度逐步抽象，避免一次性设计一套过大的 CRUD 模板体系。
