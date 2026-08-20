# ent-loom-crud

`ent-loom-crud` 是框架的核心能力组件，提供基于元数据的统一 CRUD、关系查询和统计聚合能力。它将单表 CRUD、受控跨表查询、定制 Handler、权限治理、SQL 安全和 Starter 接入收敛到同一条主链上。

## 📚 文档指南

- **[公共契约与概览](../../docs/architecture/components/crud/index.md)**: 了解 CRUD 的能力边界、核心概念和接入方式（面向使用者）。
- **能力域**:
    - [查询](../../docs/architecture/components/crud/查询.md)
    - [命令](../../docs/architecture/components/crud/命令.md)
    - [统计](../../docs/architecture/components/crud/统计.md)
    - [导入](../../docs/architecture/components/crud/导入.md)
    - [导出](../../docs/architecture/components/crud/导出.md)
    - [任务 / 文件](../../docs/architecture/components/crud/任务文件.md)
- **[内部实现细节](./docs/implementation/)**:
    - [关系查询算法与逻辑](./docs/implementation/relation-query-logic.md)
    - [单表统计引擎架构](./docs/implementation/stats-engine-logic.md)
- **[路线图](../../docs/evolution/roadmap/crud/)**:
    - [导入后续路线](../../docs/evolution/roadmap/crud/导入后续路线.md)
    - [导出后续路线](../../docs/evolution/roadmap/crud/导出后续路线.md)
    - [关系查询后续路线](../../docs/evolution/roadmap/crud/关系查询后续路线.md)
- **[架构设计决策](../../docs/evolution/decisions/crud/)**:
    - [能力操作重构决策](../../docs/evolution/decisions/crud/能力操作重构.md)

## 🏗️ 核心模块

- `ent-loom-crud-api`: 对外稳定契约 DTO、枚举、错误码。
- `ent-loom-crud-annotations`: 业务实体与 Handler 注解。
- `ent-loom-crud-core`: 核心抽象、主链编排、治理、foundation。
- `ent-loom-crud-engine-jdbc`: 默认 JDBC 执行引擎实现。
- `ent-loom-crud-import-export-excel`: Excel 导入导出格式实现。
- `ent-loom-crud-spring-boot-starter`: Spring Boot 自动装配与 Web 入口。

## 🛠️ 维护原则

- **README**: 只保留项目定位、模块总览和核心文档导航。
- **公共契约**: 统一维护在根目录 `docs/architecture/`。
- **内部实现**: 维护在模块自身的 `docs/implementation/`。
- **历史记录**: 通过 Git 历史追溯，不进入当前文档导航。
