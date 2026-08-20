# SceneValidator 场景校验提案

> 状态：Proposed，尚未实现
> 范围：业务场景字段选择与 Jakarta Validation 适配

## 背景

Jakarta Bean Validation 擅长字段格式、长度和范围，但大量业务场景需要从同一模型中选择不同字段，并额外声明本场景必填项。为每个场景增加 Validation Group 会让实体持续累积业务流程接口。

## 提案

在 Jakarta Validation 之上增加轻量场景入口：

```text
Jakarta Validation
+ SceneValidator 字段选择
+ 少量展示元数据
-> 统一 SceneError
```

目标调用形态：

```java
SceneValidator.of(requested, validator)
    .required(Order::getId)
    .validate(Order::getId, Order::getStatus);
```

方法引用只用于字段定位。字段格式继续使用 `@NotNull`、`@Size`、`@Pattern` 等标准约束；数据库唯一性、权限、状态流转和跨聚合规则仍在业务 Service 或治理层处理。

## 最小职责

| 类型 | 职责 |
|---|---|
| `SceneValidator<T>` | 选择字段、补充本场景必填、聚合错误 |
| `FieldRef<T, R>` | 方法引用到字段名的类型安全定位 |
| `@SceneField` | label、稳定 code、必填文案 |
| `ValidationScene<T>` | 将重复场景选择封装为对象 |
| `SceneError` | 稳定字段错误结构 |

第一版不新增 `@SceneLength`、`@ScenePattern` 等与 Jakarta Validation 重复的注解。业务枚举如果不能直接使用标准约束，可以单独评估一个 Jakarta Constraint。

## 错误合同

```json
{
  "field": "schoolId",
  "label": "学校",
  "code": "required",
  "message": "请选择学校",
  "rejectedValue": null
}
```

错误输出不得直接暴露 `ConstraintViolation`。必填文案优先使用场景字段配置，其余格式错误保留 Jakarta Validation 消息。

## 模块边界

提案不直接创建新 Maven 模块。只有满足以下条件才考虑 `ent-loom-validation-core`：

1. 至少三个真实模块或业务场景复用。
2. 方法引用解析、错误结构和异常策略已经稳定。
3. 能保持无 Spring、CRUD、DDL、UI 依赖。

试验实现可以先放业务 common；未经真实使用验证，不进入 `ent-loom-base` 公共 API。

## 非目标

- 不替代 Jakarta Validation。
- 不实现完整规则引擎或条件 DSL。
- 不把权限和数据库校验放入字段注解。
- 不强制所有业务使用链式 API。
- 不把本提案描述为当前框架能力。

## 接受条件

需要至少两个不同业务场景的原型，证明相比 Validation Group 或普通业务方法有明确收益，并固定方法引用解析在重构、代理和继承情况下的行为。接受前不进入 Core Contract 或当前 Architecture。
