# 强类型边界与动态载荷最佳实践

本文定义 `ent-loom-crud` 长期演进中强类型与动态结构的取舍原则，并对关键设计点给出确定方案。结论不是全链路消灭 `Map<String, Object>`，而是把动态结构限定在框架边界，把业务语义层收敛到 DTO、实体和 Patch 视图。

## 核心原则

```text
External/Dynamic Boundary -> Map/CrudRecord
Business Semantic Boundary -> DTO/Entity/Patch<T>
Persistence Adapter -> Field Map
```

框架约束：

- 入口层、治理层、默认引擎层可以使用动态结构。
- 业务 Handler、业务 Service、领域规则层优先使用强类型。
- UPDATE/PATCH 不应只用普通实体 Bean，应使用 `Patch<T>` 语义保留字段存在性。
- `Map<String, Object>` 是框架实现细节或低阶 escape hatch，不是业务编程模型。

## 分层使用规则

| 层次 | 推荐模型 | 是否允许 Map | 说明 |
|---|---|---|---|
| HTTP Controller / Facade | HTTP DTO、`Object payload`、`CrudRecord` | 允许 | 外部 JSON 是动态结构，先接住请求 |
| Spec Assembler | `CommandSpec<Object>`、`QuerySpec<?>` | 允许 | 做路由、实体解析、基础合同校验 |
| Governance | `BaseSpec`、attributes、scope dimensions | 允许 | 治理关注字段、范围、主体和审计，不承担业务语义 |
| Scene Handler | DTO、`EntityPatch<T>`、`UpdatePatch<T>`、`AggregateRelationPatch<T>` | 不推荐裸 Map | 这是业务语义边界，应尽量强类型；其中 `ACTION` DTO 和聚合 `EntityPatch<T>` 当前已支持，普通 `UpdatePatch<T>` 是后续演进目标 |
| Business Service | DTO、实体、值对象 | 不推荐 | 不应散落 `map.get("xxx")` |
| Default JDBC Engine | `WriteCommand<Map<String,Object>>` | 允许 | 动态 SQL 最终需要字段集合 |
| SQL Executor | SQL + args | 允许 | 存储适配层，不表达业务语义 |

判断标准：

- 代码在做通用路由、治理、字段过滤、SQL 拼装：可以使用动态结构。
- 代码在理解业务含义、执行业务规则：应该使用强类型。
- 代码在表达局部更新：应该使用 `Patch<T>`，而不是只用 Bean 或只用 Map。

## 当前落地边界

为了避免把长期原则误读成当前已经完整支持的 API，本文明确区分当前能力和演进目标：

| 能力 | 当前状态 | 方案口径 |
|---|---|---|
| `ACTION` DTO 入参 | 已支持 | 继续作为业务动作默认写法 |
| 聚合 UPDATE `EntityPatch<T>` | 已支持 | 继续作为聚合写入默认写法 |
| 普通单表 UPDATE `UpdatePatch<T>` | 未完整抽象 | 作为后续一等 API 补齐 |
| CREATE/UPDATE/DELETE 强类型业务基类 | 未完整抽象 | 保留底层 SPI，新增业务友好基类 |
| CREATE / SAVE_OR_UPDATE / Batch 强类型写入 | 未完整抽象 | 不进入首个最小运行版本，先保留 DTO/Map/BatchCommand 过渡 |
| 统一 payload/Patch 绑定器 | 未完整抽象 | 优先抽取，避免转换逻辑分叉 |
| 关系子项级 Patch | 未支持 | 当前只表达关系字段是否出现；子项局部更新作为后续能力 |

### 写操作覆盖矩阵

当前框架的“默认运行能力”和本文讨论的“业务强类型边界”不是同一层概念。默认 JDBC 引擎已经覆盖常见写操作；本方案首个最小运行版本只把普通单表 UPDATE/PATCH 做成强类型默认路径，其余写操作继续使用现有 DTO、`WriteCommand`、`BatchCommand` 或字段 Map 路径过渡。

| 操作 | HTTP / Facade 入口 | 默认 JDBC 引擎 | 当前业务强类型边界 | MVR 口径 | 后续方向 |
|---|---|---|---|---|---|
| `CREATE` / save | 已支持 | 已支持，字段 Map 写入 | 未提供一等 `CreateInput<T>` | 不纳入首个 MVR | 可补 `CreateInput<T>`，或继续推荐业务 DTO |
| `UPDATE` | 已支持 | 已支持，字段 Map 写入 | 聚合 UPDATE 已有 `EntityPatch<T>`；普通单表 `UpdatePatch<T>` 未完整抽象 | MVR 主路径，补齐 `UpdatePatch<T>`、binder、强类型 handler | 稳定后推广到业务模板 |
| `DELETE` | 已支持 | 已支持，按 id / targetFilters 删除或逻辑删除 | 未提供强类型 delete input | 不纳入首个 MVR | 可按业务需要补 `DeleteInput<T>` 或保持 id/filters 合同 |
| `SAVE_OR_UPDATE` | 已支持 | 已支持，按 id 存在性分派 create / update | 未提供一等 upsert Patch | 不纳入首个 MVR | 单表 UPDATE 稳定后设计 `UpsertPatch<T>` |
| `CREATE_BATCH` | 已支持 | 已支持，拆成多条 create 子命令 | 未提供批量强类型视图 | 不纳入首个 MVR | 可设计 `BatchCreateInput<T>` 或沿用 DTO + `BatchCommand` |
| `UPDATE_BATCH` | 已支持 | 已支持，拆成多条 update 子命令 | 未提供每项 `UpdatePatch<T>` 视图 | 不纳入首个 MVR | 可设计 `BatchPatch<T>` 或 `List<UpdatePatch<T>>` |
| `DELETE_BATCH` | 已支持 | 已支持，支持 `ids` 或批量子命令 | 未提供强类型 delete batch input | 不纳入首个 MVR | 可保持 `ids` 合同，必要时补批量 delete input |
| `SAVE_OR_UPDATE_BATCH` | 已支持 | 已支持，拆成多条 saveOrUpdate 子命令 | 未提供批量 upsert Patch | 不纳入首个 MVR | 待 `UpsertPatch<T>` 稳定后再设计批量形态 |
| `ACTION` | 已支持 | 由业务 handler 接管 | DTO 入参已支持 | 保持现有推荐路径 | 继续作为明确业务动作默认写法 |

覆盖结论：

- **已运行覆盖**：默认命令链路覆盖 create、update、delete、saveOrUpdate 和四类 batch。
- **已强类型覆盖**：ACTION DTO、聚合 UPDATE `EntityPatch<T>`。
- **MVR 要补齐**：普通单表 UPDATE 的 `UpdatePatch<T>`、统一 binder、强类型 UPDATE handler 和 Patch 语义测试。
- **MVR 暂不覆盖**：CREATE、DELETE、SAVE_OR_UPDATE、Batch 的一等强类型业务 API；这些操作继续走 DTO / `WriteCommand` / `BatchCommand` / 字段 Map 过渡。

## 为什么入口难以强类型

本框架不是单个业务系统里的固定 Controller-Service-DAO，而是通用 CRUD + 统一治理框架。入口需要先承接：

- 任意实体和实体组合。
- 任意操作：Query、Command、Stats、Action。
- 通用权限、数据范围、审计、幂等、字段白名单。
- HTTP JSON、SDK 调用、业务自建 Controller 等不同来源。
- 聚合写入里的主表字段、关系字段、局部更新字段。

这些能力天然要求入口能处理动态结构。因此 HTTP DTO、`CrudCommandSpecAssembler`、`CommandSpec<Object>`、`CrudRecord`、治理属性、默认字段过滤阶段保留动态载荷是合理的。

## 为什么业务层应收敛强类型

业务代码一旦开始表达业务含义，就不应该长期依赖：

```java
Map<String, Object> payload;
payload.get("studentId");
payload.get("classId");
```

这种写法的问题不是性能，而是长期可维护性：

- 字段名靠字符串，重构不可发现。
- 类型转换分散在业务代码里，异常时机不稳定。
- 字段缺失、字段为 `null`、字段类型错误容易混在一起。
- IDE、编译器、测试数据构造都无法帮助发现合同变化。
- 业务规则无法沉淀为清晰的命令对象或领域对象。

推荐业务扩展点尽量使用：

- 明确业务动作：`CommandActionSceneHandler<SubmitOrderCommand, CommandResult<?>>`。
- 明确单表局部更新：`UpdatePatch<Order>`。
- 明确聚合写入：`EntityPatch<Order>` + `AggregateRelationPatch<OrderItem>`。
- 明确服务入参：业务 DTO、领域实体、值对象。

## UPDATE/PATCH 的特殊性

UPDATE 不能简单等同于“把 payload 转成实体 Bean”。局部更新必须区分三种状态：

| 状态 | 语义 |
|---|---|
| 字段不存在 | 不更新该字段 |
| 字段存在且值为 `null` | 把该字段更新为 `null` |
| 字段存在且有值 | 把该字段更新为该值 |

普通 Bean 只能表达字段值，不能可靠表达字段是否出现在请求中。因此 PATCH 模型必须同时包含：

- 强类型实体视图。
- 主键或目标定位信息。
- `presentFields` 字段出现集合。
- 字段值访问能力。
- 给默认引擎继续执行的字段集合，但该能力应作为低阶接口处理。

当前 `EntityPatch<E>` 已经接近这个模型：

- `entity` 给业务扩展点使用强类型实体。
- `id` / `getLongId()` 给关系同步和目标定位使用。
- `presentFields` 保留 PATCH 字段存在性。
- `valuesForDelegate` 给默认引擎或特殊场景继续传递字段集合。

后续演进应继续强化 `EntityPatch<T>` / `UpdatePatch<T>` 作为业务主 API，而不是鼓励业务代码直接读写裸 `Map`。

## 方案决策

### 1. 一等 `UpdatePatch<T>` API

确定方案：新增普通单表 UPDATE 的一等 Patch 模型，作为业务 UPDATE 场景的推荐入参。`EntityPatch<T>` 保留给聚合更新场景，二者共享同一套 Patch 语义。

建议形态：

```java
public interface UpdatePatch<T> {
    Class<T> entityType();
    T entity();
    Object id();
    Long longId();
    Set<String> presentFields();
    boolean hasField(String field);
    <V> V get(String field, Class<V> targetType);
}
```

取舍：

- 不把普通实体 Bean 直接作为 UPDATE 主模型，因为 Bean 无法表达字段是否出现。
- 不把 `Map<String, Object>` 作为业务主模型，因为它把类型转换和字段合同泄漏给业务层。
- `UpdatePatch<T>` 先作为只读视图，不直接承担持久化行为，避免和默认 engine 职责混淆。

短期动作：

- 在文档、模板和新业务示例里优先使用 `UpdatePatch<T>` / `EntityPatch<T>`。
- 代码层可先通过 `EntityPatch<T>` 承载聚合更新，普通 UPDATE 的 `UpdatePatch<T>` 在后续小版本补齐。

### 2. Patch 字段集合语义

确定方案：`presentFields` / `hasField()` 表示“请求 payload 中出现过且被框架元数据识别的业务字段”，不直接等同于“该字段最终会落库”。落库字段集合需要另行表达，例如 `persistableFields`、`delegateValues` 或 `valuesForDelegate`。

字段集合分层：

- `rawFields`：请求原始字段集合，框架调试或错误报告使用。
- `presentFields`：元数据可识别的业务字段集合，包括 id、实体字段和已声明关系字段。
- `persistableFields`：可交给默认写入的主表字段集合，不包括 id、关系字段和 unknown field。
- `ignoredFields`：unknown field 或被策略忽略的字段集合，可用于严格模式错误或审计。

集合构造规则：

- `presentFields` 必须从 `rawFields` 经过实体元数据和关系元数据识别后生成，不能直接使用 `payload.keySet()`。
- `persistableFields` 必须从 `presentFields` 再经过 id、关系字段、只读字段和写入白名单过滤后生成。
- `valuesForDelegate` 只表达最终要交给默认写入链路的字段值集合，不承载主键定位语义。
- id、`targetFilters`、`expectedVersion` 属于目标定位和并发控制合同，应和写入字段集合分开传递。

建议语义：

```java
patch.hasField("orderName");          // 请求中出现过 orderName
patch.isPersistableField("orderName"); // 字段通过白名单、关系字段和 id 过滤后可交给默认写入
patch.valuesForDelegate();             // 框架/高级扩展使用的最终字段集合
```

取舍：

- 不改变 `hasField()` 的 PATCH 语义，因为业务规则通常关心“用户有没有提交这个字段”。
- 不让业务用 `valuesForDelegate.containsKey(...)` 判断业务语义，因为它已经混入持久化过滤结果。
- 不把 id、关系字段、非法字段混在一个字段集合里解释，避免业务判断和默认 engine 行为不一致。
- 不让 unknown field 进入业务 `presentFields`，否则业务规则可能对一个最终被框架忽略的字段做出误判。

短期动作：

- 修正聚合 `EntityPatch.hasField()` 的当前实现口径：从原始 `payload.keySet()` 改为已识别业务字段的请求出现性。
- 在文档和 Javadoc 中明确 `hasField()` 与 `valuesForDelegate` 的区别。
- 后续 `UpdatePatch<T>` 补充可持久化字段视图，避免业务自己比较多个 Map/Set。
- 后续 `UpdatePatch<T>` 可补充 `rawFields()` / `ignoredFields()` 只读视图，供调试、审计和严格 unknown field 策略使用。

### 3. 强类型 Scene Handler SPI

确定方案：保留现有 `CommandUpdateSceneHandler<P, R>` 作为底层 SPI，同时新增面向业务的强类型基类。业务默认继承强类型基类，不直接实现裸 SPI。

建议形态：

```java
public abstract class AbstractPatchUpdateSceneHandler<T, R>
    implements CommandUpdateSceneHandler<Object, Object> {

    protected abstract Class<T> entityType();

    protected abstract R handlePatch(
        CommandSpec<UpdatePatch<T>> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    );
}
```

取舍：

- 不破坏现有路由、注册和 delegate 机制。
- 不要求底层 registry 立即全泛型化，因为运行时按 routeKey 分发，Java 泛型无法在注册表里完整保留。
- 业务侧获得强类型入口，框架侧仍保留动态适配能力。
- 强类型基类必须承担桥接职责：进入业务前把 `Object payload` 绑定为 `UpdatePatch<T>`，调用默认链路时把 `id`、`valuesForDelegate`、`targetFilters`、`expectedVersion` 重新组装成默认引擎可识别的写入命令。

默认 delegate 桥接合同：

```java
WriteCommand<Map<String, Object>> writeCommand = new WriteCommand<Map<String, Object>>(
    CommandOperation.UPDATE,
    patch.getId(),
    patch.getValuesForDelegate(),
    spec.getTargetFilters(),
    spec.getExpectedVersion()
);
return delegate.invoke(rawSpec.toBuilder().payload(writeCommand).build());
```

桥接约束：

- 不直接把 `patch.valuesForDelegate()` 当完整 payload 传给默认引擎，否则 id 可能丢失。
- 不把 id 塞回 `valuesForDelegate()` 来规避定位问题，否则会混淆“写入字段”和“目标定位”两类合同。
- 如果调用方显式使用 `targetFilters` 定位，则桥接层应保留原始 `targetFilters`，并沿用现有 “payload.id 与 targetFilters 不能同时使用” 的入口校验规则。
- `expectedVersion` 必须随 delegate 透传，避免强类型 handler 绕过乐观锁或版本校验。

短期动作：

- ACTION 场景继续推荐 `AbstractSimpleActionHandler<P, R>`。
- UPDATE 场景补充 `AbstractPatchUpdateSceneHandler<T, R>` 或同等基类。
- 聚合 UPDATE 场景继续使用 `AbstractAggregateUpdateSceneHandler<R>`，但扩展点参数保持强类型 Patch 视图。

### 4. `valuesForDelegate` 降级为 escape hatch

确定方案：保留 `EntityPatch.getValuesForDelegate()`，但明确为低阶接口。普通业务逻辑不推荐使用它；只有需要复用默认引擎、定制字段透传、调试底层行为时才允许使用。

取舍：

- 不删除该方法，因为聚合更新需要把主表字段过滤后交给默认单表写。
- 不把它作为业务模板示例，避免业务实现重新回到裸 Map。
- 通过 Javadoc、命名说明、文档和示例共同降级，而不是用访问权限强行封死。

短期动作：

- 给 `getValuesForDelegate()` 增加 Javadoc：框架内部/高级扩展使用。
- 业务接入模板只展示 `getEntity()`、`hasField()`、`getId()`、relation items。

### 5. 统一 Payload/Patch 绑定器

确定方案：把 payload 到实体、Patch、字段集合的转换收敛到统一组件，避免多个模块各自实现转换规则。绑定器需要明确模块边界：core 定义接口、Patch 模型和最小默认实现；Spring/starter 可提供 Jackson/Validation 增强实现；JDBC engine 只消费字段 Map 结果，不反向依赖 Web 或 Jackson。

建议组件：

```java
public interface CommandPayloadBinder {
    <T> UpdatePatch<T> bindUpdatePatch(Object payload, Class<T> entityType, EntityMeta meta);
    <T> T bindEntity(Object payload, Class<T> entityType, EntityMeta meta);
    Map<String, Object> bindFieldMap(Object payload, EntityMeta meta);
}
```

统一处理：

- `Map`、`CrudRecord`、Bean、JSON 反序列化对象。
- unknown field 策略。
- `null` 值策略。
- 数字、日期、枚举、布尔转换。
- Bean Validation。
- 错误消息标准化。
- 字段 accessor / setter 缓存。

取舍：

- 不让每个 Handler 自己 `Map -> Bean`。
- 不要求默认 JDBC engine 改成 Bean 驱动，它仍然可以通过 binder 获得字段 Map。
- 不在多个模块保留重复转换逻辑，避免转换行为不一致。
- 不把 Jackson 作为 core 的强依赖，否则 core 聚合更新会被 Web 层实现细节污染。

短期动作：

- 优先抽出 `AbstractAggregateUpdateSceneHandler` 里的私有转换能力。
- 对齐 `CrudCommandSpecAssembler` 的 `ObjectMapper.convertValue` 行为。
- 统一异常类型和错误文案。

### 6. 字段名字符串风险

确定方案：短期允许 `hasField("fieldName")`，中期提供字段常量或元模型支持。字段常量不是首期强制项，但业务模板应鼓励集中管理字段名。

建议形态：

```java
rootPatch.hasField(OrderFields.ORDER_NAME);
rootPatch.get(OrderFields.ORDER_NAME, String.class);
```

取舍：

- 不在当前阶段引入复杂代码生成或 Lambda 字段解析，避免框架重量快速上升。
- 先通过字段常量降低字符串散落风险。
- 后续如果注解处理器或元数据生成体系稳定，再考虑生成 `OrderFields`。

短期动作：

- 文档示例可以继续用字符串，但注明业务项目可抽常量。
- 框架提供 `hasField(String)` 和 `get(String, Class<T>)`，不要强依赖生成器。

### 7. 关系 Patch 泛型与子项 Patch

确定方案：长期让 `AggregateRelationPatch<T>` 的泛型在业务扩展点中更自然地保留下来。当前 `List<AggregateRelationPatch<?>>` 可以保留，但应提供按 relation field 和 child type 获取的辅助方法，减少业务手动 cast。

同时明确：当前 `AggregateRelationPatch<T>` 只表达“关系字段是否出现”和“子项实体列表”，不表达每个子项内部字段的 PATCH 三态。如果后续关系同步要支持子项局部更新，应新增 `AggregateRelationItemPatch<T>`、`ChildPatch<T>` 或等价的子项 Patch 视图，不把 `AggregateRelationPatch<UpdatePatch<T>>` 作为默认形态。

建议形态：

```java
AggregateRelationPatch<OrderItem> items =
    relations.required("items", OrderItem.class);
```

取舍：

- 不强行让所有 relation specs 在 Java 类型系统里完全表达，因为聚合关系是运行时元数据驱动。
- 给常见业务场景提供强类型读取方法，减少 `@SuppressWarnings("unchecked")`。
- 保留 `AggregateRelationPatch<?>` 作为底层通用形态。
- 不把 `List<T>` 误当成子项局部更新模型；`List<T>` 适合全量替换或同步，子项局部更新需要额外 Patch 语义。
- 不把 `AggregateRelationPatch<T>` 的泛型从“子实体类型”改成“Patch 类型”，否则 `childType`、`items` 和现有关系同步语义会变得混乱。

短期动作：

- 增加 `AggregateRelationPatches` 辅助视图或工具方法。
- 示例中不要鼓励业务到处手动 cast。
- 文档中明确子项 Patch 是后续能力，当前聚合关系同步不要承诺子项字段级三态。

### 8. 架构约束测试

确定方案：文档约束需要测试或静态规则兜底。新增架构测试，限制业务示例和框架业务扩展层直接使用裸 `Map<String, Object>` 作为业务 payload。

建议规则：

- assembler、governance、engine、api record、internal binder 包允许使用动态结构。
- scene business template、sample handler、business service 示例不推荐使用裸 Map。
- `CommandSpec<Object>` 只能出现在框架适配层或底层 SPI，不作为业务推荐模板。

取舍：

- 不在框架内部全面禁止 Map，否则会破坏通用治理和默认引擎。
- 禁止范围应精确到业务扩展模板、示例和指定 package，避免误伤。
- 先以 ArchUnit 或等价静态测试表达架构边界，后续再按模块细化。

短期动作：

- 在测试中维护允许名单。
- 先约束新示例和模板，旧代码按迁移策略逐步收敛。

### 9. 迁移策略

确定方案：新代码强制推荐 DTO/Patch；旧代码允许过渡保留 Map，但不再扩展示例和模板。框架提供桥接适配器，避免一次性迁移成本过高。

迁移分期：

1. 文档和模板先切换为 DTO/Patch。
2. 新增 `UpdatePatch<T>`、强类型 UPDATE 基类、统一 binder。
3. 给旧 Map handler 增加 deprecated 或“不推荐新代码使用”的说明。
4. 逐步把业务示例从 `Map` 改到 DTO/Patch。
5. 架构测试先约束新增示例，稳定后扩大覆盖范围。

取舍：

- 不做破坏式替换，避免影响已有业务。
- 不继续扩大 Map 风格 API，避免技术债固化。
- 允许低阶 escape hatch 存在，但默认路径必须强类型。

## 推荐 API 形态

### ACTION 场景

明确业务动作优先建命令 DTO：

```java
public class SubmitOrderCommand {
    private Long orderId;
    private String reason;
}

public class SubmitOrderHandler
    extends AbstractSimpleActionHandler<SubmitOrderCommand, SubmitOrderResult> {
}
```

这类场景不应让业务实现拿 `Map<String, Object>` 自行解析。

### 普通 UPDATE 场景

普通业务更新的目标形态是 `UpdatePatch<T>`。当前版本尚未完整提供该一等 API，短期可先在业务 Service 层自建 DTO/Patch 适配，框架后续补齐统一基类和绑定器：

```java
protected CommandResult<?> handlePatch(UpdatePatch<Order> patch) {
    Order order = patch.entity();
    if (patch.hasField(OrderFields.ORDER_NAME)) {
        String orderName = patch.get(OrderFields.ORDER_NAME, String.class);
        // 处理字段出现时的业务规则
    }
    return CommandResult.success(null);
}
```

### 聚合 UPDATE 场景

聚合写入当前已支持 `EntityPatch<T>` 视图：

```java
protected void beforeUpdate(
    EntityPatch<Order> rootPatch,
    List<AggregateRelationPatch<?>> relationPatches
) {
    Order order = rootPatch.getEntity();
    if (rootPatch.hasField("orderName")) {
        // hasField 表示请求出现过该字段，不代表一定进入默认写入字段集合
    }
}
```

关系同步推荐通过强类型辅助视图获取子项。该辅助视图属于后续演进目标，避免业务直接对 `List<AggregateRelationPatch<?>>` 手动 cast：

```java
AggregateRelationPatches relationPatches = AggregateRelationPatches.of(rawRelationPatches);
AggregateRelationPatch<OrderItem> items =
    relationPatches.required("items", OrderItem.class);
```

### 默认单表写入

默认 JDBC 引擎可以继续使用字段 Map：

```java
WriteCommand<Map<String, Object>> command = resolveWriteCommand(meta, spec, CommandOperation.UPDATE);
Map<String, Object> values = command.getValues();
```

这里的 `Map` 是动态 SQL 的字段集合，不是业务层合同。

## 性能取舍

不要因为一次 `Map -> Bean` 转换就放弃业务强类型。大多数写请求的主要成本在网络、治理、数据库 I/O、事务和索引维护上。真正需要避免的是重复转换和未缓存反射：

- 避免 `Map -> Bean -> Map -> Bean` 多次来回转换。
- 在入口或业务边界只转换一次。
- 对实体字段、setter、类型转换器做缓存。
- `EntityPatch.getEntity()` / `UpdatePatch.entity()` 可以演进为懒加载，业务真正需要时再构建。
- 默认 JDBC engine 不必为了形式强类型而先转 Bean 再转回 Map。

因此性能策略是：业务语义边界强类型，底层字段集合保留 Map，转换路径短而可缓存。

## 最小运行版本确定方案

本节把最佳实践收敛为最小可运行版本（MVR）的确定交付边界。MVR 的目标不是一次补齐所有长期能力，而是打通一条可验证、可推广的默认工程路径：

```text
HTTP/Dynamic Payload
  -> CommandPayloadBinder
  -> UpdatePatch<T>
  -> AbstractPatchUpdateSceneHandler<T, R>
  -> WriteCommand(id + values)
  -> Default Command Engine
```

### 1. `UpdatePatch<T>` 真实 API 与实现

当前差距：代码层只有聚合写入使用的 `EntityPatch<T>`，普通单表 UPDATE 还没有一等 Patch 入参。业务如果要区分字段不存在、字段为 `null`、字段有值，仍需要直接读 Map 或自建临时适配对象。

确定方案：新增 `UpdatePatch<T>` 作为普通单表 UPDATE 的一等业务入参，并提供默认不可变实现。`UpdatePatch<T>` 与 `EntityPatch<T>` 保持一致的 PATCH 语义，但不混用聚合关系职责。

MVR API：

```java
public interface UpdatePatch<T> {
    // JavaBean 风格方法作为主 API，和现有 EntityPatch<T> 保持一致
    Class<T> getEntityType();
    T getEntity();
    Object getId();
    Long getLongId();
    Set<String> getPresentFields();
    Map<String, Object> getValuesForDelegate();

    // 业务可读性别名可作为 default method 提供
    Class<T> entityType();
    T entity();
    Object id();
    Long longId();
    Set<String> presentFields();
    boolean hasField(String field);
    boolean isPersistableField(String field);
    <V> V get(String field, Class<V> targetType);
    Map<String, Object> valuesForDelegate();
}
```

取舍：

- `UpdatePatch<T>` 放在 core 的 command/patch 语义包内，接口面向业务扩展，默认实现统一命名为 `DefaultUpdatePatch<T>`，构造只由 binder、框架基类和测试直接使用。
- `UpdatePatch<T>` 暴露 `valuesForDelegate()`，但定位为框架内部/高级扩展 escape hatch，普通业务规则只使用 `entity()`、`hasField()`、`get()` 和 `id()`。
- `UpdatePatch<T>` 主 API 优先保留 `getEntity()`、`getId()`、`getValuesForDelegate()` 等 JavaBean 风格方法，和现有 `EntityPatch<T>` 一致；`entity()`、`id()`、`valuesForDelegate()` 可作为 default alias，避免业务代码可读性和框架 API 风格冲突。
- `presentFields` 表示请求中出现且被元数据识别的业务字段，`isPersistableField` 表示通过持久化过滤后的字段，二者不能合并。
- MVR 不把 `rawFields()` / `ignoredFields()` 作为业务主路径强制暴露；binder 内部必须保留这些集合，后续如果要做严格 unknown field、审计或调试响应，再公开只读视图。
- MVR 不引入懒加载实体，也不引入代码生成字段元模型；先保证语义正确和 API 稳定。
- `EntityPatch<T>` 暂不强行改名或替换，避免影响聚合更新现有使用方式；后续可抽公共只读 Patch 视图减少重复。

验收标准：

- 字段不存在、字段存在且为 `null`、字段存在且有值三种状态可被测试区分。
- `hasField(id)` 与 `isPersistableField(id)` 的语义清晰，id 可用于定位但默认不作为普通更新字段。
- unknown field 不进入 `presentFields` 和 `valuesForDelegate`；如需追踪，进入 `ignoredFields` 或等价调试视图。
- `valuesForDelegate()` 返回不可变 Map。
- 通过 `getXxx()` 与短别名访问同一份不可变状态，二者语义不能分叉。

### 2. 统一 `CommandPayloadBinder`

当前差距：聚合 UPDATE 在 `AbstractAggregateUpdateSceneHandler` 内部维护一套 `payload -> Map -> EntityPatch` 私有转换逻辑，默认 JDBC 写入在 `CommandPayloadMapper` 内维护另一套 `payload -> Map` 规则。两处对 Bean、`CrudRecord`、类型转换、unknown field 和错误消息的支持范围不一致。

确定方案：新增公共绑定器，统一负责 payload 到实体、Patch、字段 Map 的转换。聚合更新、普通 UPDATE 基类、默认 JDBC command mapper 逐步改为依赖该绑定器或复用其规则。模块归属必须先固定，避免把 Web/Jackson 细节反向塞进 core。

模块边界：

| 模块 | 职责 |
|---|---|
| `ent-loom-crud-core` | 定义 `CommandPayloadBinder`、`UpdatePatch<T>`、默认反射绑定实现和 Patch 语义测试 |
| `ent-loom-crud-spring-boot-starter` | 提供 Jackson/Bean Validation 增强绑定实现，接 HTTP 请求转换 |
| `ent-loom-crud-engine-jdbc` | 消费 binder 产出的字段 Map 或保持等价规则，不依赖 Web 层 |

MVR 能力：

- 支持 `Map`、`CrudRecord`、普通 Bean。
- 支持实体字段写入和字段 Map 生成。
- 支持 `String`、数字、布尔、枚举、`Date`、`BigDecimal`、`BigInteger` 的基础转换。
- 明确 unknown field、`null`、空字符串 id、类型转换失败的异常语义。
- 绑定结果同时产出 `presentFields` 和 `valuesForDelegate`，避免业务自己比较多个 Map/Set。
- 绑定结果能够区分 `rawFields`、`presentFields`、`persistableFields` 和 `ignoredFields` 的语义，即使 MVR 暂不全部公开成 API。

取舍：

- `CommandPayloadBinder` 是 core 合同，不依赖 Spring、Jackson、Servlet 或 Bean Validation；默认实现使用反射和框架元数据完成最小转换。
- `CommandPayloadMapper` 不再继续扩展自己的转换规则，MVR 后应成为 JDBC engine 的薄适配层，或者在行为上通过合同测试与 binder 保持一致。
- MVR 先使用反射实现，字段和 setter 缓存作为后续优化；不要在首版引入复杂 BeanIntrospector 或代码生成。
- 默认策略采用“unknown field 忽略且不进入业务 Patch、持久化字段按 `EntityMeta.allowedFields` 过滤、类型转换失败抛 `ValidationException`”。如果未来需要严格 unknown field，可增加配置项。
- Bean Validation 不作为 MVR 必选项；先预留扩展点，避免把校验生命周期和绑定器职责绑死。
- 默认 JDBC engine 不改成 Bean 驱动，仍消费字段 Map；binder 只统一转换规则，不改变存储适配层模型。
- core 默认实现不依赖 Jackson；Spring/starter 可以用 Jackson 增强转换能力，但行为必须通过 binder 合同测试对齐。

验收标准：

- `AbstractAggregateUpdateSceneHandler` 不再保留一套独立私有转换规则。
- `CommandPayloadMapper` 与 binder 的 Map/CrudRecord 行为一致。
- 转换失败的异常类型和错误消息可预测。

### 3. 强类型 UPDATE Handler 基类

当前差距：业务要定制普通 UPDATE 时，默认仍会直接面对 `CommandUpdateSceneHandler<Object, Object>` 和 `CommandSpec<Object>`，需要自己做 payload 解析、id 判断、字段过滤和 delegate payload 重组。

确定方案：保留 `CommandUpdateSceneHandler<P, R>` 作为底层 SPI，新增 `AbstractPatchUpdateSceneHandler<T, R>` 作为业务默认入口。业务实现只处理 `UpdatePatch<T>`，routeKey、payload 绑定、delegate payload 透传由基类处理。

建议形态：

```java
public abstract class AbstractPatchUpdateSceneHandler<T, R>
    implements CommandUpdateSceneHandler<Object, Object> {

    protected abstract Class<T> entityType();

    protected abstract R handlePatch(
        CommandSpec<UpdatePatch<T>> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    );
}
```

取舍：

- 基类构造方式参考 `AbstractSimpleActionHandler`，由业务传入 entity class、scene、结果类型等最小信息，基类统一生成 routeKey。
- 基类实现 `CommandUpdateSceneHandler<Object, Object>`，避免改动现有 router、registry 和注解注册链路。
- 业务回调拿强类型 `CommandSpec<UpdatePatch<T>>`，但 delegate 仍保留原始 `CommandSpec<Object>` 链路，保证默认 engine 可复用。
- 基类应提供一个受保护的默认透传方法，把 `patch.getId()`、`patch.getValuesForDelegate()`、`targetFilters` 和 `expectedVersion` 组装为 `WriteCommand<Map<String, Object>>` 后调用 delegate，避免业务重复写桥接代码。
- 默认透传方法必须把主键定位和写入字段分开处理；`valuesForDelegate` 不允许为了通过默认引擎校验而重新混入 id。
- MVR 不追求 registry 全泛型化；运行时路由由 `CrudRouteKey` 决定，泛型只服务业务编程体验。
- CREATE/DELETE 的强类型基类不放入 MVR；先解决 UPDATE/PATCH 这个语义风险最高的场景。

验收标准：

- 新业务 UPDATE 示例不需要直接实现 `CommandUpdateSceneHandler<Object, Object>`。
- Handler 内不需要手写 `Map -> Bean` 或 `Map -> Patch`。
- Handler 可以选择调用 delegate，也可以完全接管业务写入。
- Handler 调用默认 delegate 时不会丢失 id、`targetFilters` 或 `expectedVersion`。

### 4. CREATE / SAVE_OR_UPDATE / Batch 边界

当前差距：如果把所有写操作都纳入首个强类型化范围，会同时引入 create 默认值、upsert 分支判定、batch 单项错误定位等额外语义，导致 UPDATE/PATCH 主路径被拖慢。

确定方案：MVR 只把普通单表 UPDATE/PATCH 做成强类型默认路径。CREATE、SAVE_OR_UPDATE、Batch 写入暂不新增一等 Patch API，继续使用现有 DTO、`WriteCommand`、`BatchCommand` 或字段 Map 路径过渡。

取舍：

- CREATE 没有字段三态问题，业务动作可优先用 DTO；默认 CREATE 继续走字段 Map，不阻塞 UPDATE/PATCH 的收敛。
- SAVE_OR_UPDATE 同时包含 create/update 语义，字段存在性、id 判定和默认值策略更复杂，不放进首个 MVR。
- Batch 写入需要表达每个 item 的独立 id、字段出现性和错误定位，不能简单复用单个 `UpdatePatch<T>`。
- 不把所有写操作一次性强类型化，避免为了形式统一扩大 API 面和迁移成本。

后续方向：

- CREATE 可补 `CreateInput<T>` 或直接沿用 DTO 作为业务推荐模型。
- SAVE_OR_UPDATE 可在单表 UPDATE 稳定后设计 `UpsertPatch<T>`。
- Batch 可在单项 Patch 语义稳定后设计 `BatchPatch<T>` 或 `List<UpdatePatch<T>>` 视图。

### 5. PATCH 语义测试

当前差距：文档已经定义三态和字段集合分层，但这些语义还没有通过普通单表 `UpdatePatch`、聚合 `EntityPatch` 和 delegate payload 的共同测试固定下来。尤其需要修正聚合场景里 `presentFields` 直接来自原始 `payload.keySet()` 的风险。

确定方案：为 Patch 语义增加单元测试和最小集成测试。文档约束必须有自动化测试兜底，尤其是三态、字段过滤和 delegate payload 的边界。

MVR 测试范围：

- 字段不存在：`hasField=false`，实体字段保持默认值，delegate 不包含该字段。
- 字段存在且为 `null`：`hasField=true`，`get(field, type)=null`，delegate 包含该字段且值为 `null`。
- 字段存在且有值：实体、`get()`、delegate 值一致。
- id 字段：可定位，默认不作为普通更新字段。
- unknown field：默认忽略，不进入 `presentFields` 和 delegate；严格模式后续可报错，测试必须固定当前策略。
- 关系字段：聚合场景下出现在 `presentFields`，但不进入主表 delegate payload。
- delegate 桥接：强类型 UPDATE handler 透传默认引擎时，使用 `WriteCommand.id` 承载主键，使用 `WriteCommand.values` 承载写入字段，不能依赖 payload map 中混入 id。
- 并发控制：强类型 UPDATE handler 透传默认引擎时，`expectedVersion` 不能丢失。
- 目标过滤：强类型 UPDATE handler 透传默认引擎时，`targetFilters` 不能丢失，且继续遵守入口层对 `payload.id` 与 `targetFilters` 互斥的约束。
- 聚合 `EntityPatch.presentFields`：unknown field 不进入集合，已声明关系字段进入集合，主表可写字段进入集合。

取舍：

- MVR 测试先落在 core，覆盖 binder、Patch 实现和 handler 基类；JDBC engine 只补充 payload map 行为与 binder 对齐的合同测试。
- MVR 不引入完整 ArchUnit 规则；先用行为测试证明 API 语义。
- 测试优先覆盖 binder、`UpdatePatch`、强类型 handler 基类和聚合 handler 的共同边界。
- 架构约束测试放到下一阶段，用来限制新示例和业务模板继续扩散裸 Map 风格。

### 6. 文档、模板与 Javadoc 默认路径

当前差距：业务接入模板仍保留裸 `CommandUpdateSceneHandler<Object, Object>` 风格示例，`EntityPatch.getValuesForDelegate()` 也没有在 API 文档层明确标成低阶透传接口。

确定方案：业务接入模板、示例代码和 Javadoc 必须把 DTO/Patch 作为默认路径，把裸 `Map<String, Object>` 明确标为底层 SPI 或 escape hatch。

MVR 文档更新：

- `business-integration-template.md` 增加 `AbstractPatchUpdateSceneHandler<T, R>` 示例。
- `typed-boundary-best-practices.md` 保持当前能力和演进目标的区别，避免把未落地 API 写成已可用 API。
- `EntityPatch.getValuesForDelegate()`、`UpdatePatch.valuesForDelegate()` 增加 Javadoc，说明仅用于默认引擎透传、框架内部或高级扩展。
- 旧 `CommandUpdateSceneHandler<Object, Object>` 示例保留时必须说明是底层 SPI 示例，不作为新业务推荐写法。

取舍：

- 不立即删除旧 Map 示例，避免历史用户无法对照迁移。
- 新示例不再展示业务代码中 `payload.get("xxx")` 的写法。
- 业务模板优先展示“业务校验后调用默认 delegate”的路径，因为这是绝大多数 UPDATE 定制的最低成本写法；完全接管写入只作为补充说明。
- MVR 不要求所有历史文档一次性重写，但入口索引和业务模板必须先改。

### 7. 错误与校验策略

当前差距：类型转换、id 归一化、日期/枚举解析目前分散在不同私有实现里，失败时机和错误文案不稳定。没有统一 binder 之前，业务 Handler 很容易把输入结构错误、业务规则错误和持久化错误混在一起处理。

确定方案：绑定器负责输入结构和类型转换错误，业务 Handler 负责业务规则错误，默认 engine 负责持久化执行错误。三类错误不要混在同一层处理。

MVR 策略：

| 场景 | 处理方式 |
|---|---|
| payload 为 `null` | 绑定为空 Patch 或按操作要求报缺少 id |
| id 缺失或空字符串 | UPDATE 抛 `ValidationException` |
| unknown field | 默认忽略，不进入实体、`presentFields` 和 delegate；后续可配置严格模式 |
| 类型转换失败 | 抛 `ValidationException`，包含字段名、目标类型和原始值摘要 |
| enum 不匹配 | 抛 `ValidationException` |
| 日期格式不支持 | 抛 `ValidationException` |
| Bean Validation | MVR 预留扩展点，后续接入 |

取舍：

- unknown field 默认忽略，符合通用 CRUD 框架兼容性；严格模式后续通过配置打开。
- 不在 binder 中执行业务必填、状态流转、权限规则，这些留给治理层或业务 Handler。
- 空字符串 id 在 UPDATE 中按缺失处理并报错，普通字符串字段仍按业务字段值保留；不要把所有空字符串都全局转成 `null`。
- 错误消息要稳定，但不暴露过长原始 payload，避免日志和响应污染。

### 8. 关系 Patch 强类型辅助视图

当前差距：聚合更新当前已经有 `AggregateRelationPatch<?>`，但业务需要手动按 relation field 查找并 cast，容易把关系字段名、子实体类型和列表语义写散。这个问题影响体验，但不影响普通单表 UPDATE MVR 的主路径。

确定方案：关系 Patch 辅助视图作为最佳实践项，但不阻塞单表 UPDATE MVR。下一阶段新增 `AggregateRelationPatches`，减少业务手动 cast。

建议形态：

```java
AggregateRelationPatches relations = AggregateRelationPatches.of(rawRelations);
AggregateRelationPatch<OrderItem> items = relations.required("items", OrderItem.class);
Optional<AggregateRelationPatch<OrderItem>> optionalItems = relations.find("items", OrderItem.class);
```

取舍：

- MVR 保留 `List<AggregateRelationPatch<?>>`，因为聚合关系是运行时元数据驱动，无法完全依赖 Java 泛型表达。
- 辅助视图只做 relation field 和 child type 校验，不改变现有关系同步模型。
- 子项级字段三态不放入 MVR；当前 `List<T>` 表达关系项集合，不表达每个子项的 PATCH 字段出现性。
- 后续子项级局部更新使用 `AggregateRelationItemPatch<T>` 或 `ChildPatch<T>`，不把 `AggregateRelationPatch<UpdatePatch<T>>` 作为默认 API。

### 9. 架构约束与迁移

当前差距：文档原则还没有形成可执行边界。短期如果直接禁止 Map，会误伤入口、治理和默认 engine；如果完全不约束，业务示例又会继续复制裸 Map 风格。

确定方案：MVR 先提供可用默认路径；架构约束测试和旧 Map handler deprecated 放到最佳实践增强阶段。迁移策略是“新代码默认强类型，旧代码允许低阶 SPI 过渡”。

取舍：

- 不对框架内部 Map 使用做全局禁止，入口、治理、engine 仍然需要动态结构。
- 不立即给底层 SPI 加 `@Deprecated`，避免误导使用者认为能力不可用；先在 Javadoc 中标注“不推荐业务新代码直接使用”。
- 架构测试先约束示例、模板和 demo，再逐步扩展到业务扩展包。

## 实施优先级

| 优先级 | 事项 | 原因 |
|---|---|---|
| P0 | 明确当前能力边界 | 防止把演进目标误读成已落地 API |
| P0 | 新增 `UpdatePatch<T>` 与默认不可变实现 | 普通 UPDATE 需要一等 Patch 语义，这是 MVR 主路径 |
| P0 | 抽出统一 `CommandPayloadBinder` | 避免转换逻辑分叉，并支撑 Patch 三态 |
| P0 | 新增 `AbstractPatchUpdateSceneHandler<T, R>` | 让业务 UPDATE 默认强类型 |
| P0 | 增加 PATCH 三态与字段过滤测试 | 把核心语义变成可验证合同 |
| P0 | 明确 `hasField()` 与最终写入字段集合的区别 | 防止 PATCH 业务判断和持久化过滤结果混用 |
| P0 | 修正聚合 `EntityPatch.presentFields` 语义 | 避免 unknown field 被业务误判为已识别字段 |
| P0 | `valuesForDelegate` 标注为 escape hatch | 防止业务继续依赖底层 Map |
| P1 | 更新业务接入模板和 Javadoc | 把默认写法从裸 SPI 迁到 DTO/Patch |
| P1 | 统一错误与校验策略 | 保证 binder、handler、engine 的失败边界稳定 |
| P2 | 关系 Patch 强类型辅助视图 | 减少聚合关系场景 cast |
| P2 | 字段常量或元模型 | 降低字符串字段名风险 |
| P2 | 架构约束测试 | 把原则变成可执行边界 |
| P3 | 关系子项级 Patch 语义 | 子项局部更新需要字段三态，不能只靠 `List<T>` |
| P3 | 旧 Map handler 迁移与 deprecated | 平滑收敛历史实现 |

## 最终约定

`ent-loom-crud` 的长期设计约定是：

> 通用入口弱类型，业务语义边界强类型，局部更新使用 Patch，存储适配层使用字段 Map。

这条规则同时满足通用治理框架的灵活性、业务代码的可维护性，以及默认 JDBC 引擎的实现效率。后续实现应优先补齐 `UpdatePatch<T>`、强类型 Handler 基类和统一 Payload/Patch 绑定器，把文档原则落成默认工程路径。
