# 实体 DAO 自定义方法

> 状态：Proposal（首期实现与安全修复已完成）<br>
> 决策日期：2026-09-18<br>
> 关联文档：[实体 DAO](./实体DAO.md)

## 目标

`@EntDao` 不只提供主键 CRUD，还应允许业务以面向对象的方法扩展单实体 DAO。业务 Service 只依赖一个 DAO 接口；框架统一处理 SQL 参数绑定、返回值映射、逻辑删除和数据范围。分页暂不属于首期闭环。

```java
@EntDao
public interface ProductDao extends EntityDao<Product, Long> {

@EntQuery("""
        select *
        from product
        where category_id = :categoryId
          and status = 'AVAILABLE'
        order by id desc
        """)
    List<Product> findAvailableByCategory(Long categoryId);

    @EntCommand("""
        update product
        set price = :price
        where id = :id
        """)
    int updatePrice(Long id, BigDecimal price);
}
```

## 当前复核结论

首期基础能力与本轮安全修复已经完成；当前实现仍严格限制在下述 SQL 子集内，超出边界的声明继续在启动期拒绝：

- 原始 `WHERE` 含 `OR` 时，治理条件必须与完整业务条件组合为 `(业务条件) AND (治理条件)`，不能直接在原字符串末尾追加 `AND`。
- SQL 注释必须在启动期拒绝，至少拒绝 `--`、`#` 和 `/* */`，避免治理条件被注释吞掉。
- 顶层逗号多表、别名后的第二张表和多表写入必须拒绝；只识别第一张表不足以证明治理安全。
- `T` / `Optional<T>` 查询必须限制最多读取两行，不能先把无界结果集全部加载到内存再判断基数。
- 标注自定义注解的方法必须始终参与启动期校验；不能通过重写 `EntityDao` 基础方法或声明 `default` 方法绕过校验。
- 用户参数绑定必须沿用实体字段的 JDBC 类型规范化策略；无法可靠推断参数字段类型时，首期应拒绝声明。

上述问题属于实现约束，不改变业务接口目标；无法证明安全的 SQL 统一判定为不支持，并提示改用专用 Repository。

## 能力边界

```mermaid
flowchart LR
    service["业务 Service<br/>授权 / 事务 / 业务编排"] --> dao["ProductDao<br/>统一面向对象入口"]
    dao --> basic["EntityDao<br/>基础主键 CRUD"]
    dao --> query["@EntQuery<br/>自定义只读查询"]
    dao --> command["@EntCommand<br/>自定义写命令"]
    basic --> governance["DAO 治理执行层<br/>范围 / 逻辑删除 / 参数绑定 / 映射"]
    query --> governance
    command --> governance
    governance --> database[("MySQL 8")]
    service --> repository["专用 Repository<br/>跨聚合事务 / 特殊数据库能力"]
    repository --> database
```

- `EntityDao<T, ID>`：保留标准主键 CRUD。
- `@EntQuery`：只允许读取，首期支持单对象、可选对象、列表和 DTO。
- `@EntCommand`：统一承载自定义更新、删除；插入待范围归属规则明确后再加入。
- 专用 Repository：只承载跨聚合事务、复杂批量编排或数据库特有能力，不作为普通自定义查询的默认去处。
- 不提供方法名推导 SQL，查询和命令必须显式表达 SQL。

## 返回类型

结果类型以 DAO 方法的泛型返回类型为唯一来源，不在注解中重复声明 `result = Product.class`。框架在启动期读取 `Method#getGenericReturnType()` 并完成校验。

| 返回类型 | 语义 |
|---|---|
| `T` | 必须返回一条记录 |
| `Optional<T>` | 返回零或一条记录 |
| `List<T>` | 返回多条记录 |
| `int` / `long` | 写命令影响行数 |

`T` 可以是实体或明确的 DTO。首期不支持原始容器、方法级动态泛型和复杂嵌套泛型，例如原始 `List`、`<T> List<T>`、`Map<String, List<T>>`；声明不合法时启动失败。

首期不定义 `Page<T>`、`PageRequest` 或自动 count SQL 合同。

## 查询与命令

```mermaid
flowchart TD
    method["扫描 @EntDao 方法"] --> kind{"方法注解"}
    kind -->|"@EntQuery"| queryCheck["校验只读 SQL<br/>解析 T / Optional / List"]
    kind -->|"@EntCommand"| commandCheck["校验写 SQL<br/>校验 int / long 返回值"]
    queryCheck --> scope["合并不可放宽的数据范围<br/>与逻辑删除谓词"]
    commandCheck --> scope
    scope --> bind["命名参数绑定"]
    bind --> execute["参与调用方事务并执行"]
    execute --> map["结果映射 / 影响行数校验"]
```

两类注解必须拆分，因为读写在允许的 SQL、返回类型、事务提示和影响行数语义上不同：

| 规则 | `@EntQuery` | `@EntCommand` |
|---|---|---|
| 允许 SQL | `SELECT` | `UPDATE`、`DELETE` |
| 实体或 DTO 映射 | 支持 | 不支持 |
| 分页 | 首期不支持 | 不支持 |
| 影响行数 | 不适用 | `int` / `long` |
| 只读事务提示 | 可应用 | 不应用 |

首期不增加 `@EntInsert`、`@EntUpdate`、`@EntDelete`。框架根据 SQL 语句类型校验 `@EntCommand`；出现必须显式区分命令类型的真实需求后，再考虑增加枚举属性。

## 治理规则

自定义方法必须继承 `EntityDao` 的安全边界，不能成为绕过范围治理的旁路：

- SQL 值只能通过命名参数绑定，禁止拼接外部输入。
- 查询、更新和删除默认自动合并已解析的数据范围与逻辑删除谓词；调用参数只能继续收窄，不能覆盖或关闭治理约束。
- SQL 必须先解析为受限 SQL AST，再生成治理条件和最终 SQL，禁止对原始 SQL 直接查找、截取和拼接治理条件。首期要求能够确定唯一治理实体、写入目标和安全追加位置；无法证明时启动失败。
- 普通业务方法不提供 `scope = NONE` 一类的逃生参数；确需全量维护时使用职责明确的专用 Repository。
- `@EntQuery` 中出现写语句、`@EntCommand` 中出现读语句或返回类型不匹配时，应用启动失败。
- DAO 自动参与调用方事务，但动作授权和跨聚合事务仍由 Service 负责。

自定义方法与基础 CRUD 使用同一条代理执行链：

```text
@EntDao 代理
  -> 从 DAO 泛型或显式 entity 得到 EntityType
  -> 每次调用解析 EntityAccessScope
  -> 结构化解析并校验 SQL
  -> 追加 RowConstraint 与逻辑删除谓词
  -> 绑定用户参数和框架参数
  -> 通过 GuardedSqlExecutor 参与调用方事务执行
```

代理保持无请求状态，不能缓存请求级范围；自定义方法不得绕过 `EntityDaoFactory.scoped(...)` 或现有受治理 JDBC 执行器。

### 实体推断与 SQL 子集

- 默认从 `@EntDao` 接口的 `EntityDao<T, ID>` 泛型推断治理实体；无法解析确定实体、一个 DAO 涉及多个实体，或 SQL 表无法与实体元数据确认对应时，才要求 `entity = ...` 显式声明。
- 首期支持能够通过受限 AST 确定唯一治理实体的单表 SQL，允许普通表别名、带括号的 `AND/OR` 条件、命名参数、常量、`IN`、白名单函数、投影和 `ORDER BY`。
- 首期禁止 SQL 注释、顶层逗号多表、`JOIN`、子查询、CTE、`UNION`、多表写入、复杂聚合、窗口函数、任意动态 SQL 及其他无法确定治理锚点或安全改写位置的语句。
- 白名单函数仅用于字段表达式；未列入白名单的函数、`FOR UPDATE` 以及 `UPDATE/DELETE` 的排序或分页子句在启动期拒绝，避免改写后语义变化。
- `T` / `Optional<T>` 首期不接受业务侧 `LIMIT/OFFSET`，由框架按方言追加最多两行的限制；`List<T>` 仍不提供分页合同。
- 别名不是安全边界；治理条件必须追加到已确认的实体表别名上。用户参数禁止使用 `__ent_` 前缀，框架生成参数统一使用该保留前缀。

## 实施范围

采用较小闭环：首期先支持静态显式 SQL、命名参数、实体/DTO/列表/可选对象返回、可确定唯一治理实体的单表查询与更新删除、范围治理和 JDBC 执行。分页、插入范围策略和复杂 SQL 改写暂缓；方法名推导、XML Mapper、任意动态 SQL、ORM Session、脏检查、懒加载和自动关系导航不在首期实现。

首期不追求通用 SQL 兼容性。若暂不引入成熟 SQL Parser，则只实现能够覆盖上述语法子集的受限词法和 AST；解析失败、语法超出白名单或治理位置不明确时，启动失败，不继续增加字符串解析特例。

框架尚未发布，该能力直接按目标合同重构：删除被替代的“自定义抽象方法一律失败”路径及测试，不保留旧代理、适配层、兼容开关或新旧双轨。

## 较小闭环修复方案

### 第一阶段：先恢复安全边界

- [x] 用表达式 AST 生成 `WHERE (业务条件) AND (范围条件 AND 逻辑删除条件)`，覆盖查询、更新和物理删除；逻辑删除转换也必须复用同一表达式节点。
- [x] 解析阶段统一识别并拒绝 SQL 注释、顶层逗号多表、多表写入、分号和未闭合引号/括号。
- [x] 明确表解析结果只能包含一张实体表和一个可选别名；别名之后只能出现合法语句子句。
- [x] Starter 扫描时先处理自定义注解；禁止注解覆盖 `EntityDao` 基础方法，禁止注解 `default` 方法，确保所有自定义方法启动期校验。

### 第二阶段：统一执行合同

- [x] `T` / `Optional<T>` 查询按方言追加最多两行限制，并在两行时抛出不唯一异常。
- [x] 参数绑定记录参数对应字段，统一复用 `JdbcEntityValueBinder`；集合参数只允许出现在 `IN` 参数位置。
- [x] 无法推断字段类型、返回类型或治理锚点时，统一以启动期 `ValidationException` 拒绝。

### 第三阶段：最小回归验证

- [x] 增加 `OR` 条件下范围和逻辑删除不可绕过的查询、更新、物理删除测试。
- [x] 增加 SQL 注释、顶层逗号多表、多表更新和基础方法重写的启动失败测试。
- [x] 增加单对象查询最多读取两行的验证，以及枚举、`LocalDate`、`LocalDateTime` 参数绑定测试。

## 实施清单

### 首期闭环

- [x] 定义 `@EntDao` 自定义方法扫描与启动期校验流程。
- [x] 支持 `@EntQuery` 静态显式 SQL、命名参数和单表 `SELECT`。
- [x] 支持 `@EntCommand` 静态显式 SQL、命名参数和单表 `UPDATE` / `DELETE`。
- [x] 首期限制为可确定唯一治理实体的单表 SQL；遇到 JOIN、子查询、CTE、复杂聚合等无法安全改写的语句时启动失败，并提示改用专用 Repository。
- [x] 支持实体、明确 DTO、`List<T>`、`Optional<T>` 返回类型。
- [x] 支持 `int` / `long` 写命令影响行数返回；自定义命令默认返回驱动报告的影响行数，单行成功/未命中语义另按方法合同声明。
- [x] 实现命名参数绑定、结果映射和基础异常处理。
- [x] 将查询、更新、删除统一纳入已解析的数据范围与逻辑删除治理。
- [x] 校验调用参数只能收窄数据范围，不能关闭或覆盖治理条件。
- [x] 统一参与调用方事务；授权和跨聚合事务继续由 Service 负责。
- [x] 对 SQL 类型、返回类型、参数绑定和治理改写失败执行启动期校验。
- [x] 补充单表查询、更新、删除、范围治理和非法声明的最小验证用例。

### 首期边界确认

- [x] 明确专用 Repository 的使用条件：跨聚合事务、复杂批量或数据库特有能力。
- [x] 明确 SQL 解析失败、返回类型不匹配和治理无法证明安全时的错误信息。
- [x] 用商城 `ProductDao.findForOrder` 真实业务样例验证接口合同；下单事务仍由 `PlaceOrderService` 编排，自定义 DAO 查询下单所需投影并参与调用方事务。

### 暂缓项目

- [ ] 暂缓 `INSERT` 自定义命令及新增数据的自动范围字段策略，待范围归属规则明确后再实现。
- [ ] 暂缓 `Page<T>`、自动总数查询和复杂分页 SQL，待分页合同与 count SQL 规则稳定后再实现。
- [ ] 暂缓 JOIN、子查询、CTE、复杂聚合等复杂 SQL 的结构化改写支持。
- [ ] 暂缓方法名推导 SQL、XML Mapper 和任意动态 SQL。
- [ ] 暂缓 `@EntInsert`、`@EntUpdate`、`@EntDelete` 等更细粒度注解。
- [ ] 暂缓 ORM Session、脏检查、懒加载和自动关系导航。
- [ ] 暂缓原始容器、方法级动态泛型和复杂嵌套泛型返回值。
