# ent-loom

0.util EnumBoolean 公共轻包
1.实体 添加 公用附加信息 (允许执行:(约定<规则<配置))


2.实体同步数据库表结构
3.实体读取UI配置内容
4.实体开放统一标准接口(内部/外部)
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
@EntDbMeta

3.
@EntUiEntity
@EntUiIndex
@EntUiField
@EntUiMeta

4.
@EntApiEntity
@EntApiIndex
@EntApiField
@EntApiMeta

5.
@EntDocEntity
@EntDocField
@EntDocMeta

ent-loom
ent-loom-base
ent-loom-meta
ent-loom-module
ent-loom-module-db
ent-loom-module-ui

## V1 Maven模块骨架

```
ent-loom
	ent-loom-base
	ent-loom-meta
	ent-loom-module (pom)
		ent-loom-module-db (pom)
			ent-loom-module-db-core
			ent-loom-module-db-mysql
		ent-loom-module-ui (pom)
			ent-loom-module-ui-user
			ent-loom-module-ui-admin
```

依赖方向（V1）：

- ent-loom-meta -> ent-loom-base
- ent-loom-module-db-core -> ent-loom-meta
- ent-loom-module-db-mysql -> ent-loom-module-db-core
- ent-loom-module-ui-user -> ent-loom-meta
- ent-loom-module-ui-admin -> ent-loom-meta

## 重构约定（强制）

- 命名或模型重构时，不做旧代码兼容层，不保留 deprecated 过渡方案。
- 直接全量替换到新规范（包含注解名、包名、模块名、引用点）。
- 如需回滚，使用 Git 版本回退，不通过代码内双轨兼容实现。
