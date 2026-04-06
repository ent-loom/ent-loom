# ent-loom-ddl

目标：承接 `framework-java-to-db` 中“Java 实体 -> MySQL DDL”相关能力，做成更清晰、可扩展、可控执行的重构版实现。

参考来源：`/Users/zubin/IdeaProjects/baoyi-cloud-back/framework-cloud/framework-java-to-db/README.md`

## 1. 适合归类到 ent-loom-ddl 的需求

| 编号 | 需求项 | 适配度 | 归类结论 |
|---|---|---|---|
| R1 | 启动期自动建库（`CREATE DATABASE IF NOT EXISTS`） | 高 | 核心能力，纳入 |
| R2 | 自动建表与增量更新（表不存在 `CREATE`，存在则 `ALTER`） | 高 | 核心能力，纳入 |
| R3 | 按差异块处理：表注释、主键、字段、索引 | 高 | 核心能力，纳入 |
| R4 | 字段重命名迁移（`renameFrom`） | 高 | 核心能力，纳入 |
| R5 | 索引能力（普通/唯一/表达式） | 高 | 核心能力，纳入 |
| R6 | 分级执行策略（仅建表/允许修改/允许删除） | 高 | 核心能力，纳入 |
| R7 | 扫描实体包并构建代码侧表结构模型 | 高 | 核心能力，纳入 |
| R8 | Java 类型到 SQL 类型映射与默认值推导 | 高 | 核心能力，纳入 |
| R9 | 大表风险防护（高风险变更保护） | 高 | 核心能力，纳入 |
| R10 | 字段名正则注入默认值、表规模设定 | 中 | 放策略层或配置扩展，不绑定核心算法 |

## 2. 重构式架构建议（java 实体 -> ddl）

建议把 `ent-loom-ddl` 拆成“协议稳定 + 核心可替换 + 扩展可插拔”的结构：

1. `ent-loom-ddl-annotations`
2. `ent-loom-ddl-core-model`：统一元模型（实体、字段、索引、主键、差异对象）
3. `ent-loom-ddl-core-scan`：实体扫描与注解解析
4. `ent-loom-ddl-core-introspect`：数据库侧结构读取与反解析（先支持 MySQL）
5. `ent-loom-ddl-core-diff`：差异计算（create/alter/rename/drop）
6. `ent-loom-ddl-core-sql-mysql`：MySQL SQL 生成器
7. `ent-loom-ddl-core-plan`：执行计划与策略过滤（runType、安全开关）
8. `ent-loom-ddl-core-exec`：SQL 执行与结果采集
9. `ent-loom-ddl-spring-boot-starter`：启动期自动执行接入
10. `ent-loom-ddl-metadata`（可选）：管理后台元数据输出

## 3. 与当前注解能力的对齐与缺口

当前已具备（可直接复用）：

1. `@EntDbEntity`：`table`、`schema`、`comment`、命名策略
2. `@EntDbField`：类型、长度、nullable、unique、default、comment、持久化开关、生成策略
3. `@EntDbIndex/@EntDbIndexes`：索引名、字段、唯一、索引类型

建议补充（支撑 R3/R4/R5/R9 的关键缺口）：

1. 主键定义能力：单主键/复合主键（建议新增注解或字段属性）
2. 字段重命名：`renameFrom`（字段级至少需要）
3. 索引表达式支持：函数索引或原生表达式索引
4. 删除策略开关：字段/索引删除是否允许（全局 + 局部）
5. 表规模级别：`SMALL/MEDIUM/LARGE`（用于风险策略）

## 4. MVP 分期（推荐）

1. M1：实体扫描 + 元模型 + MySQL `CREATE DATABASE/TABLE`
2. M2：字段/索引差异 `ALTER`（新增、修改）
3. M3：`renameFrom` + 执行策略分级（含删除保护）
4. M4：大表风险防护（高风险变更拦截/告警）
5. M5：元数据输出模块（可选，独立于 ddl-core）

## 5. 非核心边界（避免 ddl-core 过重）

以下能力建议不直接塞进 `ddl-core`，而是放扩展模块：

1. 通用后台 UI 字段元信息与路由语义
2. `module`、`entityId` 等业务菜单配置语义
3. 强业务定制的字段名模式推断规则

这样可以保证 `ent-loom-ddl` 核心长期稳定，扩展能力按业务自由演进。
