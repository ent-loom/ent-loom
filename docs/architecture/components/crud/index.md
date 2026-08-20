# CRUD 架构文档入口

> 状态：Current
> 最近核验：2026-08-21

本目录描述 CRUD 的当前合同和实现。完整的 CRUD 领域导航见 [CRUD 领域总览](../../../domains/crud/index.md)。阅读时先看总览，再按问题进入具体文档。

## 总览

1. [CRUD 架构总览](架构总览.md)
2. [运行时架构](运行时架构.md)

## 公开合同

- [HTTP 契约](HTTP契约.md)
- [查询命令协议与路由](查询命令协议.md)
- [强类型边界](强类型边界.md)

## 能力域

- [查询](查询.md)
- [命令](命令.md)
- [统计](统计.md)
- [导入](导入.md)
- [导出](导出.md)
- [任务 / 文件](任务文件.md)

## 默认实现

- [默认引擎](默认引擎.md)
- [运行时注册表](运行时注册表.md)
- [关系查询实现](../../../../ent-loom-modules/ent-loom-crud/docs/implementation/relation-query-logic.md)
- [Stats 引擎实现](../../../../ent-loom-modules/ent-loom-crud/docs/implementation/stats-engine-logic.md)

## 演进

- [CRUD 设计决策](../../../evolution/decisions/crud/index.md)
- [CRUD 路线图](../../../evolution/roadmap/crud/index.md)

本目录不保存实施步骤或历史方案，未完成目标统一进入 Roadmap。
