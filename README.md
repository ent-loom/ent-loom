# ent-loom
目标:
0.util EnumBoolean 公共轻包
1.实体 添加 公用附加信息 (允许执行:(约定<规则<配置))


2.实体同步数据库表结构
3.实体开放统一标准接口(内部/外部)
4.实体读取UI配置内容
5.实体开放统一标准文档
6.项目ER图总览

7.变更影响分析(标准可读更变说明,测试建议)
8.标准化测试/高级测试
9.安全合规(埋点审计)
10.插件通道




1.
@EntEntity
@EntIndex
@EntField
@EntMeta

2.
@EntDbEntity
@EntDbIndex
@EntDbField

4.
@EntUiEntity
@EntUiIndex
@EntUiField
@EntUiMeta



## V1 Maven模块骨架
ent-loom
    ent-loom-base (StrUtil级公共层)
    ent-loom-meta (实体-属性基本元素)
    ent-loom-module
        ent-loom-ddl
            ent-loom-ddl-annotations
            ent-loom-ddl-schema（最新实体信息,枚举 同步到 表结构）
        ent-loom-crud （JSON -> Spec -> Dialect -> PreparedSql -> Executor）(MYSQL/PostgreSQL)
            ent-loom-ddl-crud-base     
            ent-loom-ddl-crud-read     (查)
            ent-loom-ddl-crud-cud     （增删改）
        ent-loom-ddl-crud-spring-boot-starter (多一个controller门面 可以开关)
        ent-loom-ui
            ent-loom-ui-annotations (设置 某个string角色 展示增删改查导出哪些按钮,字段太多需要分组显示,哪些字段允许排序,键集分页还是页码分页,哪些字段不显示不编辑等)
            ent-loom-ui-core 


## 重构约定（强制）

- 重构时, 不要兼容,不要过滤,直接改为最佳干净的结果。(都是 全新代码)
