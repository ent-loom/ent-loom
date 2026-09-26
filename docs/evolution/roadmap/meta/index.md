# Meta 路线图

> 状态：Remaining
> 上游契约：[元数据约定与裁决契约](../../../architecture/core/元数据约定与裁决契约.md)

Meta 的完整文档导航见 [Meta 领域总览](../../../domains/meta/index.md)。

Meta -> CRUD / DOC / DDL 的静态 Adapter 已形成当前闭环。这里仅跟踪尚未完成的跨模块裁决能力，不重复当前架构和历史实施过程。

## 当前主线

[元数据裁决实施计划](元数据裁决实施计划.md) 的阶段 1-4 已完成。[实体必填约束与统一校验](../../decisions/core/实体必填约束与统一校验.md) 已完成创建最小闭环；以下继续跟踪剩余范围。

### 实体必填约束闭环

- [x] 将 CRUD 外部输入要求收敛到 `CrudInputContract`，按资源编码和别名配置创建输入字段、更新禁改字段。
- [x] 分离实体结构语义和输入提示；默认值、只读、生成字段及主键策略不再通过实体 `required` 表达。
- [x] 将 `inputRequired` 投影到 DOC/CRUD Runtime Model，并由公共校验器覆盖默认创建、强类型 Handler、DAO 命令创建及其批量 CREATE 子命令。
- [ ] 建立业务默认值的统一赋值阶段，并补齐只读、应用生成字段在各阶段的处理。
- [ ] 为独立导入链补齐批次记录位置等错误上下文。
- [ ] 更新链保留字段存在性，区分未传与显式清空；生成字段按阶段豁免。
- [ ] label 默认文案已完成；继续补齐业务文案覆盖、字段路径、错误码和批量记录位置。
- [ ] 同步 Doc/UI 展示与服务端约束边界，保持 DDL 可空性独立。
- [x] 移除商城 Customer/Product 实体上的重复 Validation 注解，保留复杂业务 DTO 校验，并更新指南状态。
- [x] 已验收空白字符串、空数组/集合/Map、null、合法 0/false、primitive 字段存在性、字段例外、生成主键、创建入口和 label 文案；剩余默认值/应用生成、局部更新及导入错误上下文。

### 实体属性与数据库列映射统一

依据 [实体属性与数据库列映射边界](../../decisions/core/实体属性与数据库列映射边界.md)，属性识别与单列映射首期已实施：

- [x] 建立公共属性分类与列资格结果，保留可解析元素类型、推断来源和关系事实；统一 `transient`、继承字段及字段隐藏规则。
- [x] 使用 `@EntField(persisted = OptionalBoolean.FALSE)` 排除标量；三态意图区分未声明与显式值，DDL 原生冲突沿用既有诊断。
- [x] 统一 Module-only、Meta-first、CRUD 原生解析与 Meta Adapter 的列资格规则；明确引擎支持的单列类型集合。
- [x] DDL、JDBC Mapper、Binder 和普通写入消费统一结果；DOC 保留非列属性的业务描述。
- [x] 注册阶段拒绝非列主键、逻辑删除字段、物理列治理字段和索引；普通列查询拒绝非列筛选与排序。
- [x] 验收标量、枚举、日期、实体对象、实体列表、普通 POJO、Map、数组及显式排除；名称注解不改变列资格。
- [x] 验收未支持的显式存储失败，以及受支持的显式单列映射；不同关系属性共享同一关联源键时分别保留。
- [x] 商城订单携带明细列表并由详情 Handler 手动装配；静态 H2 验收覆盖 DDL 建表、DAO 单表读写与非列属性排除。

剩余工作：JSON／自定义转换器按真实需求后续扩展；类型变量与嵌套泛型的实际元素解析后续单独设计，当前保留未知结果，不猜测类型。

## 后续目标

| 优先级 | 目标 | 启动条件 |
|---|---|---|
| P1 | DOC Resolver 收敛 | 公共 Resolver 已有两个真实消费者 |
| P2 | UI Adapter | UI Runtime Model 和 API 稳定 |
| 后续 | 包扫描式实体发现 | 显式 class list 成为明确瓶颈 |
| 远期 | OpenAPI 投影、动态刷新 | 核心静态模型稳定后单独决策 |

## 已完成且不再跟踪

- Meta Annotation -> Descriptor。
- Meta -> CRUD / DOC / DDL 静态 Adapter。
- Native-only、Meta-only、Meta + Module 基础路径。
- CRUD 唯一 Runtime Model 与冻结 Registry。

## 维护规则

- 当前事实维护在 `docs/architecture/core/meta/`。
- 本页只保存长期剩余目标。
- 当前阶段任务只在实施计划维护。
- 已完成阶段从路线图删除，必要的设计理由提炼为 Decision。
