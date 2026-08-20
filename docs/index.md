# ent-loom 文档中心

> 状态：Current
> 最近核验：2026-08-21

本文档中心只回答四类问题：系统现在是什么、如何使用、为什么这样设计、下一步做什么。实现细节尽量留在对应模块，历史材料不参与当前架构判断。

## 文档结构

```text
architecture  当前事实和必须遵守的架构契约
guides        接入、开发和使用方式
evolution     设计取舍与尚未完成的演进计划
```

权威顺序固定为：

```text
Core Contract
  -> Component Architecture
  -> Guide
  -> Evolution Decision / Roadmap
```

下游文档只能细化上游契约，不能覆盖它。发现冲突时，以更上游且状态有效的文档为准，并修正冲突文档。

## 从这里开始

1. [系统架构总览](architecture/overview.md)：模块、依赖、建模和执行主链。
2. [Core 架构入口](architecture/core/index.md)：跨模块必须共同遵守的契约。
3. [组件架构入口](architecture/components/index.md)：CRUD 等组件当前如何工作。
4. [使用指南](guides/index.md)：业务项目如何接入。
5. [演进记录](evolution/index.md)：为什么这样设计，以及尚未完成什么。

## Core Contract

- [元数据约定与裁决契约](architecture/core/metadata-resolution-contract.md)：定义属性来源、逐属性裁决、诊断、模块独立运行和消费者闭环。
- [组件边界与依赖规则](architecture/core/component-boundaries.md)：定义 Core、Adapter、Starter 和业务项目之间的依赖方向。
- [治理执行主链](architecture/core/governance/pipeline.md)：定义请求进入执行器前必须经过的治理阶段。

## 当前组件

- [CRUD](architecture/components/crud/index.md)：Query、Command、Stats、Import、Export 和 Task/File。
- [Meta 建模](architecture/core/meta/layering-summary.md)：通用 Meta 输入、模块原生输入和 Adapter 投影。
- [DOC 实现说明](../ent-loom-modules/ent-loom-doc/docs/implementation.md)

## 常用指南

- [CRUD 开发指南](guides/crud/development-guide.md)
- [CRUD 业务集成模板](guides/crud/integration-template.md)
- [Meta-first 最佳实践](guides/meta/meta-first.md)
- [Export 展示值配置](guides/crud/export-rendering.md)

## 演进入口

- [设计决策](evolution/decisions/index.md)
- [路线图](evolution/roadmap/index.md)
- [当前实施计划](evolution/roadmap/current.md)

## 文档状态

非索引文档应在标题后声明状态：

| 状态 | 含义 |
|---|---|
| `Current` | 已由当前代码或稳定契约支持 |
| `Target` | 已接受但尚未完全落地的规范性目标 |
| `In Progress` | 正在实施，不能代替当前架构事实 |
| `Remaining` | 已确认但尚未开始或未完成的路线 |
| `Superseded` | 已被其他文档替代，只保留决策背景 |

## 维护规则

1. 一个结论只保留一个权威正文，其他文档使用链接。
2. `architecture` 不保存实施步骤；`roadmap` 不保存已完成方案全文。
3. 决策完成后保留取舍和后果，删除临时迁移清单与重复 API 说明；历史通过 Git 追溯。
4. 当前事实、目标状态和历史背景必须明确分开。
5. 移动或删除文档后必须检查仓库内 Markdown 相对链接。
