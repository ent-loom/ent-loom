# RequiredInferenceFilter 边界与实施清单

> 状态：In progress，作为“实体必填约束与统一校验”的项目级推断增量
> 范围：普通 CRUD 创建场景的项目级必填推断  
> 实施策略：先完成单过滤器、静态元数据和模型诊断；复用已完成的创建校验闭环

## 结论

`RequiredInferenceFilter` 是现有 `Module Project Convention` 来源下、只针对 `required` 属性的项目级规则。它必须进入现有 `Contribution` 与 `PropertyContributionResolver` 裁决链，不建立新的优先级或 Runtime Model。本文不重新定义创建校验、错误结构或适配器投影。

第一阶段只支持固定的 `CREATE` 场景。过滤器仅读取实体元数据、字段元数据和场景标识；不读取请求值、数据库状态、租户/用户状态、时间、随机数或隐式线程上下文。未启用过滤器时，现有显式规则、Validation 推断和未声明语义保持不变。

## 第一阶段契约

### 输入

- 实体元数据；
- 字段元数据；
- 固定场景标识 `CREATE`。

`CREATE` 是模型构建输入的一部分，参与缓存键和诊断。首个闭环不扩展 UPDATE、PATCH 或查询场景；后续场景必须形成独立模型或明确的模型键。

### 输出

过滤器返回三态贡献：

- `TRUE`：贡献 `required=true`；
- `FALSE`：贡献 `required=false`；
- `UNSET`：不贡献 `required`。

每条贡献携带 `source`、稳定 `ruleId` 和 `reason`。`TRUE/FALSE/UNSET` 是过滤器贡献值，不是最终 Runtime Model 值。过滤器必须无副作用、可重复执行；相同输入必须得到相同结果。异常按现有诊断策略处理，不得静默转换为 `FALSE`。

## 裁决规则

`required` 仅按 `required` 属性逐属性裁决，遵循现有元数据契约：

```text
字段显式 required
> 生成/只读专属规则
> 项目 RequiredInferenceFilter（Module Project Convention）
> Validation 补充推断
> 未声明
```

- 显式 `required=true/false` 双向覆盖过滤器贡献；
- `UNSET` 不产生贡献，继续进入下一优先级；
- 最终未声明不自动等同于 `FALSE`，由既有 Runtime Model 语义决定；
- 生成、只读、`inputRequired`、默认值等属性保持独立；只有明确贡献 `required` 的规则才参与本属性裁决；
- 过滤器不得直接改写其他属性；生成/只读规则也不得通过“类型可写”猜测业务必填。

第一阶段只允许注册一个 `RequiredInferenceFilter`。发现多个实例时按启动诊断策略报告并失败；多过滤器组合、排序和冲突协议后置。

## 推荐策略与边界

项目可显式启用保守策略：生成/只读字段由其专属规则处理，字符串字段返回 `UNSET`，其他明确可写字段返回 `TRUE`。该策略不是全局默认；启用时必须输出新增/变化字段清单，并支持实体或字段排除。过滤器只负责贡献 `required`，`inputRequired`、默认值和创建阶段豁免继续由既有规则计算。

动态业务条件、跨字段不变量、租户差异、数据库状态和请求值判断进入业务校验策略，不通过过滤器表达。

## 诊断要求

每个字段至少保留：

| 信息 | 含义 |
|---|---|
| entity / field | 实体和字段标识 |
| contributionValue | 过滤器贡献的 `TRUE/FALSE/UNSET` |
| finalValue | 最终 `TRUE/FALSE/未声明` |
| source / ruleId | 最终获胜来源和稳定规则标识 |
| reason | 人可读原因 |
| overridden | 被覆盖的候选来源，可为空 |

首个闭环提供启动诊断或模型检查，按实体、字段和 `CREATE` 场景查看贡献与最终结果。异常、同级冲突和无法投影的强约束按现有诊断契约处理；不新增运行时旁路校验。

## 实施清单

### 首个闭环（必须完成）

- [x] 将过滤器归入 `Module Project Convention`，复用现有 Resolver 和 Runtime Model。
- [x] 固定 `CREATE` 场景；模型缓存键和完整诊断待后续模型层接入。
- [x] 定义三态贡献、`source`、`ruleId`、`reason` 及异常策略。
- [x] 明确显式 `required=true/false` 双向覆盖、`UNSET` 透传和最终未声明语义。
- [x] 保证过滤器无副作用、可重复执行，不读取动态上下文。
- [ ] 限制为单过滤器注册，多个实例启动失败并可诊断。
- [x] 增加模型级回归验证：显式 true/false 覆盖、`UNSET` 透传、来源和原因诊断。
- [ ] 复用既有创建校验用例，验证推断必填时缺失值被拒绝、显式可选时允许缺失；不在过滤器中重复实现校验。

### 第二步（首个闭环稳定后）

- [ ] 增加 Native-only 与 Meta-enabled 结果一致的回归验证。
- [ ] 增加真实 CRUD `CREATE` 链路验证，覆盖默认值补齐、生成字段和 `inputRequired` 分离。
- [ ] 增加实体/字段排除及启动时新增/变化字段清单验证。

### 明确不做

- [ ] UPDATE、PATCH、查询等其他场景。
- [ ] 多过滤器组合、排序和冲突协议。
- [ ] 按租户、用户、请求字段或数据库状态动态推断。
- [ ] 改写 `inputRequired`、默认值、只读或生成策略。
- [ ] 将“其他可写字段默认必填”升级为所有项目的全局默认。
- [ ] 在过滤器内重复实现创建校验、错误文案或适配器投影。

## 完成标准

满足以下条件后，可将本补充标记为 Accepted：

1. 过滤器契约、`CREATE` 场景、可读取上下文和异常策略明确。
2. 结果通过现有 Resolver 形成唯一 Runtime Model。
3. 显式 required 双向覆盖、`UNSET` 和未声明语义有回归验证。
4. 最终结果可查看贡献值、来源、规则标识、原因和覆盖链。
5. 既有创建校验仍消费同一个 Runtime Model；过滤器没有旁路规则。
6. Native-only、Meta-enabled 与真实 CRUD 创建校验行为一致。
7. 动态业务校验与元数据推断的职责边界在文档和示例中一致。
