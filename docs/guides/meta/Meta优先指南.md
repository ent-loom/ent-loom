# Meta 优先使用指南

> 状态：Current
> 最近核验：2026-08-21

推荐用 Meta 注解声明跨模块业务事实，只在需要组件专属策略时增加 CRUD、DOC 等组件注解。

```text
Meta 描述业务事实
Module Annotation 描述执行或展示策略
Descriptor 是通用中间契约
Runtime Model 是组件最终执行契约
```

架构边界见 [Meta 分层与运行模型](../../architecture/core/meta/分层与运行模型.md)，属性裁决规则见
[元数据约定与裁决契约](../../architecture/core/元数据约定与裁决契约.md)。

## 依赖选择

| 场景 | 业务依赖 |
|---|---|
| Meta-first | `ent-loom-meta-annotations`，再按需引入组件注解和 Starter |
| CRUD-only | `ent-loom-crud-annotations` 及 CRUD 运行模块 |
| DOC-only | DOC 注解及 DOC 运行模块 |
| 框架扩展 | 按需依赖 `ent-loom-meta-contract` |

业务不应把聚合 POM 当作运行时 API 依赖，也不应直接依赖 Adapter 实现模块。

## 属性与数据库列的边界

`@EntField` 用于业务元数据描述。支持的单列值类型默认映射当前实体表列，实体对象、集合、Map 和普通 POJO 默认不映射列，仍保留业务描述。为复杂属性添加中文名称不改变列资格：

```java
@EntField("订单明细")
private List<OrderItem> orderItemList;

@EntField(value = "客户名称", persisted = OptionalBoolean.FALSE)
private String customerName;
```

`OptionalBoolean` 来自 `com.entloom.base.common`。`persisted` 默认 `UNSET` 按类型推断，`FALSE` 排除临时标量，`TRUE` 显式要求受支持的单列存储。复杂属性上的 `TRUE` 当前会报错；JSON／转换器扩展尚未开放。框架不支持 `exist` 或 `persistent` 参数。

非列属性不参与普通单表写入和列筛选／排序，关系装配与聚合写入仍需遵循各自契约。完整规则见
[实体属性与数据库列映射边界](../../evolution/decisions/core/实体属性与数据库列映射边界.md)。

## 通用字段

只表达通用语义时，仅使用 Meta 注解：

```java
@EntEntity
public class Student {
    @EntField("姓名")
    private String studentName;

    @EntField("备注")
    private String remark;
}
```

`EntField` 只表达实体字段事实。CRUD 创建输入要求和更新禁改字段由 `@EntCrudEntity` 声明，Doc/UI 使用运行时合并后的 `inputRequired` 作为输入提示；复杂业务校验仍由 Service / Handler 负责。

当前创建和更新输入边界已接入默认 CRUD、DAO 命令和强类型 Handler。类型映射、执行边界及配置示例见 [CRUD 外部输入契约与统一校验](../../evolution/decisions/core/实体必填约束与统一校验.md)。

数据库可空性独立：DDL 默认非空，可空例外使用 `@EntDdlField(nullable = OptionalBoolean.TRUE)`，主键不可为空。默认值另行声明，不自动补零或空字符串。

通用关系同样由 Meta 声明，外键引用优先标注在 ID 属性上：

> 默认弱关系采用“唯一映射自动补全，加载与授权显式选择”，见 [核心契约](../../architecture/core/元数据约定与裁决契约.md#21-实体关系与结构属性默认弱关系)。结构属性只需保留 `@EntField`，CRUD 复用已声明外键及确定的属性类型补全映射；普通查询不会加载关联。商城示例通过请求展开和字段授权复用通用查询，无需手工查询服务或重复的组件标记。

```java
@EntField("客户 ID")
@EntRelation(targetEntity = "customer")
private Long customerId;

@EntField("客户")
private Customer customer;
```

`targetEntity` 显式指定已注册的目标实体标识；`sourceField` 为空时取被注解字段名，`targetField` 默认 `id`，关系基数默认多对一。

CRUD 将 `customerId` 上的关系按 `Id` 后缀约定绑定到 `customer` 单对象导航属性，并校验其类型必须与目标实体一致；该规则由 CRUD Core 提供，CRUD-only 使用 `@EntCrudField` 声明外键时同样适用。绑定结果通过 `INFERRED_VALUE_USED` 诊断保留规则来源；同名属性类型不符产生 `RELATION_NAVIGATION_TYPE_MISMATCH` ERROR，默认拒绝启动。

没有 `customer` 属性时，关系仍以 `customerId` 为关系字段存在；不会按类型搜索其他不同名属性，也不会仅因对象属性存在就创建关系。`customerId` 保留数据库列映射，`customer` 不映射当前表列。该绑定只生成关系元数据，不自动加载或开启级联操作。

非标准命名可以在导航属性上显式声明映射，例如 `@EntRelation(targetEntity = "customer", sourceField = "customerId") private Customer buyer;`；此时不再在 ID 属性上重复声明同一关系。

集合关系可以复用子实体已声明的唯一反向引用，无需重复声明目标、外键和基数：

```java
@EntField("订单明细")
private List<OrderItem> orderItemList;

// OrderItem 上的外键关系事实。
@EntRelation(targetEntity = "order")
private Long orderId;
```

CRUD 根据 `List<OrderItem>` 与唯一反向多对一引用补全 `Order.id → OrderItem.orderId`。这仅产生候选映射，HTTP 仍需显式 `expandRelations` 和父资源授权。Java 判定器须显式返回 `EXPAND` 字段能力；缺失或空集合关闭展开，仍受实体静态字段上限约束。多个反向外键时不猜测，需要在集合上通过 `@EntCrudField` 指定目标字段及基数；无关系事实或泛型未知时不生成映射。显式一对多省略 `sourceField` 时使用当前实体主键；自动补全则保留反向关系的实际连接键。

## 组件覆盖

只有出现组件专属行为时才增加组件注解。例如 CRUD 需要覆盖默认连接策略：

```java
@EntField("客户 ID")
@EntRelation(targetEntity = "customer")
@EntCrudField(joinType = JoinType.INNER)
private Long customerId;
```

DOC 的展示名称、示例和关系备注也只在与 Meta 通用定义不同时覆盖。不要为了“注册能力”给每个字段机械添加
`@EntCrudField`、`@EntDocField`，否则同一业务事实会被重复维护。

## 覆盖规则

项目使用数据库生成主键时，统一声明：

```yaml
ent:
  loom:
    meta:
      defaults:
        id-policy: DATABASE
```

Meta 主键语义包含 `DATABASE`、`APPLICATION`、`ASSIGNED`；默认 `UNSET`。字段例外使用
`@EntMetaId(policy = EntIdPolicy.ASSIGNED)`。已有应用生成器声明推导为 `APPLICATION`，与
`DATABASE` 或 `ASSIGNED` 同时声明会产生错误诊断。策略描述不自动提供应用生成器实现。

CRUD 将数据库生成映射为 `GENERATED` 并回填主键；MySQL DDL 将其映射为单列整数主键自增。
优先级为模块原生显式声明、Meta 字段显式声明、模块全局默认、Meta 全局默认、框架约定。
DDL 的 `NONE` 可显式关闭数据库生成；模块覆盖只改变自己的最终模型，SQL 执行前会校验
已注册 CRUD 模型与 DDL 模型的主键契约。`MetaCrudAdapter.idPolicySource()` 与
`MetaDdlAdapter.generationStrategySource()` 可查询最终策略的配置来源。

`@EntEntity(entity = ...)` 定义逻辑资源名。物理表名按类名去掉 `Entity` 后缀并转下划线推导，
可由原生 CRUD / DDL 表名配置分别覆盖；同时启用时两者必须一致。
联合主键允许使用契约一致的手工赋值字段，逐字段校验列映射，不支持数据库自动生成。
即使 `diagnostics.fail-fast=false`，DDL 模型中的 ERROR 诊断仍阻止输出可执行元数据；
宽松模式可通过 `diagnostics()` 查询错误，警告不阻止建表。

当前有效原则：

1. 组件显式注解可覆盖 Meta 显式属性。
2. 注解默认值不能冒充显式声明并覆盖上游语义。
3. 多来源按单个属性裁决，不整模型替换。
4. 同级冲突必须产生诊断，不能依赖 Bean 或扫描顺序。
5. Meta-first 与 Module-only 最终汇聚到同一个组件 Runtime Model 和 Registry。

统一 Contribution 与属性级 Resolver 已完成当前闭环；各组件的装配和已验证路径以
[Meta Runtime Adapters](../../architecture/core/meta/运行时适配器.md) 为准。

## 使用边界

- 关系声明属于建模；关系加载、JOIN、远程调用和回填属于 CRUD 执行层。
- 权限、主体和数据范围属于治理层，不进入 Meta 注解。
- DDL 方言、DOC 示例、UI 控件等组件专属信息不进入通用 Meta。
- 当前 Starter 主要通过显式实体类名列表装配，不提供运行期动态实体发现。
- DDL Adapter 已接入 Starter，共用 Meta 解析器；`ent.loom.meta.ddl.enabled=false` 可关闭该适配。UI 尚无正式 Meta Adapter，不应按已实现能力使用。

迁移现有实体时按业务域逐步进行：先提取稳定业务事实到 Meta，再只保留必要的组件覆盖，并通过启动期诊断及
对应 Adapter 集成测试验证结果。
