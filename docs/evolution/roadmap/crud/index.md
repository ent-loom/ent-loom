# CRUD 路线图

> 状态：Remaining
> 当前事实：[CRUD 架构文档入口](../../../architecture/components/crud/index.md)

CRUD 的完整文档导航见 [CRUD 领域总览](../../../domains/crud/index.md)。

## 当前主线

- [CRUD 重构路线](CRUD重构路线.md)：合同门禁、`UpdatePatch<T>`、Starter 包名、Core 边界和架构守卫。
- [场景策略治理实施计划](场景策略治理实施计划.md)：高风险场景能力点与治理接入。
- [统一 CRUD 执行模式实施计划](统一CRUD执行模式实施计划.md)：基础 CRUD、可选实体模式与定制 Handler 的统一主链。
- [实体 DAO 实施清单](实体DAO实施清单.md)：D0-D3、D4.2-D4.3 已完成；可信范围绑定、最小主键 CRUD、JDBC 与 MySQL 8 全链路已验收，剩余 Web 装配边界、数据库类型矩阵及真实需求扩展按清单推进。

## 能力增强

- [导入](导入后续路线.md)
- [导出](导出后续路线.md)
- [任务 / 文件](任务文件后续路线.md)
- [关系查询](关系查询后续路线.md)

能力增强不得越过重构路线中的合同门禁和模块边界。已完成能力以 Architecture 为准，不在路线图重复列出。
