# ent-loom 文档中心

> 状态：Current
> 最近核验：2026-09-22

ent-loom 是面向实体编程的业务框架。文档按“先使用、再理解、后维护”组织；当前代码和稳定契约优先于设计目标与实施记录。

> 文档维护：ent-loom Documentation Team · [反馈文档问题](https://github.com/ent-loom/ent-loom/issues)

## 先做什么

| 你的目标 | 从这里开始 |
|---|---|
| 第一次运行项目 | [快速开始](guides/快速开始.md) |
| 接入 CRUD 业务 | [CRUD 使用指南](guides/crud/index.md) |
| 设计实体和字段 | [Meta 优先指南](guides/meta/Meta优先指南.md) |
| 了解模块边界 | [系统架构总览](architecture/架构总览.md) |
| 查阅稳定合同 | [Core Contract](architecture/core/index.md) |

## 文档怎么分

- [使用指南](guides/index.md)：面向业务开发者的接入、调用和配置步骤。
- [领域入口](domains/index.md)：按 CRUD、DDL、Meta 聚合相关文档，不复制权威正文。
- [当前架构](architecture/index.md)：只描述代码已经支持的模型、边界和执行合同。
- [设计与维护](evolution/index.md)：记录设计取舍、路线图和验收过程，主要面向框架维护者。

## 阅读规则

权威顺序为：`Core Contract → Component Architecture → Guide → Decision / Roadmap`。
下游文档可以补充上游内容，但不能把目标能力写成当前能力；发现冲突时，以状态为 `Current` 且更靠前的文档为准。

非索引文档在标题后声明状态：`Current` 表示当前支持，`Target` 表示目标合同，`In Progress` 表示正在实施，`Remaining` 表示尚未完成，`Accepted` 表示有效的设计决策，`Superseded` 表示已被替代。

一个结论只保留一个权威正文。架构文档不承载实施步骤，路线图不重复完整 API 说明；验收命令、阶段清单和历史背景统一放在维护区。

## 联系方式

- 文档反馈：[GitHub Issues](https://github.com/ent-loom/ent-loom/issues)
- 技术讨论：[GitHub Discussions](https://github.com/ent-loom/ent-loom/discussions)
