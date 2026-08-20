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

## 通用字段

只表达通用语义时，仅使用 Meta 注解：

```java
@EntField(
    value = EntFieldKind.TEXT,
    label = "姓名",
    required = OptionalBoolean.TRUE
)
private String studentName;
```

通用关系同样由 Meta 声明：

```java
@EntField(value = EntFieldKind.REF_ID, label = "班级")
@EntRelation(
    targetEntity = "class",
    sourceField = "classId",
    targetField = "id",
    cardinality = RelationCardinality.MANY_TO_ONE
)
private Long classId;
```

`sourceField` 为空时默认使用被注解字段名，可省略重复配置。

## 组件覆盖

只有出现组件专属行为时才增加组件注解。例如 CRUD 需要目标类型或加载策略：

```java
@EntField(value = EntFieldKind.REF_ID, label = "班级")
@EntRelation(targetEntity = "class", targetField = "id")
@EntCrudField(
    targetClass = SchoolClass.class,
    scope = RelationScope.LOCAL_DB,
    joinType = JoinType.LEFT
)
private Long classId;
```

DOC 的展示名称、示例和关系备注也只在与 Meta 通用定义不同时覆盖。不要为了“注册能力”给每个字段机械添加
`@EntCrudField`、`@EntDocField`，否则同一业务事实会被重复维护。

## 覆盖规则

当前有效原则：

1. 组件显式注解可覆盖 Meta 显式属性。
2. 注解默认值不能冒充显式声明并覆盖上游语义。
3. 多来源按单个属性裁决，不整模型替换。
4. 同级冲突必须产生诊断，不能依赖 Bean 或扫描顺序。
5. Meta-first 与 Module-only 最终汇聚到同一个组件 Runtime Model 和 Registry。

统一 Contribution 与属性级 Resolver 仍在实施中，当前落地范围以
[Meta Runtime Adapters](../../architecture/core/meta/运行时适配器.md) 为准。

## 使用边界

- 关系声明属于建模；关系加载、JOIN、远程调用和回填属于 CRUD 执行层。
- 权限、主体和数据范围属于治理层，不进入 Meta 注解。
- DDL 方言、DOC 示例、UI 控件等组件专属信息不进入通用 Meta。
- 当前 Starter 主要通过显式实体类名列表装配，不提供运行期动态实体发现。
- DDL/UI Adapter 尚未形成当前闭环，不应按已实现能力使用。

迁移现有实体时按业务域逐步进行：先提取稳定业务事实到 Meta，再只保留必要的组件覆盖，并通过启动期诊断及
对应 Adapter 集成测试验证结果。
