# ent-loom 文档中心

> 状态：Current
> 最近核验：2026-08-21

本文档中心只回答四类问题：系统现在是什么、如何使用、为什么这样设计、下一步做什么。实现细节尽量留在对应模块，历史材料不参与当前架构判断。

## 文档结构

```text
domains       按领域聚合的阅读入口，不新增权威正文
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

1. [系统架构总览](architecture/架构总览.md)：先了解模块、依赖、建模和执行主链。
2. [CRUD 领域总览](domains/crud/index.md)：按当前架构、业务接入、设计决策和路线图阅读 CRUD。
3. [Meta 领域总览](domains/meta/index.md)：按核心契约、实现参考、使用指南和路线图阅读 Meta。
4. [Core Contract](architecture/core/index.md)：查看跨模块必须共同遵守的契约。
5. [使用指南](guides/index.md)：按文档类型查找业务接入说明。
6. [架构演进](evolution/index.md)：查看设计取舍和未完成目标。

## 按文档类型查看

- [架构文档](architecture/index.md)：当前事实、稳定契约和实现边界。
- [使用指南](guides/index.md)：业务项目的接入、开发和配置方式。
- [设计决策](evolution/decisions/index.md)：为什么采用当前方案，以及方案后果。
- [路线图](evolution/roadmap/index.md)：尚未完成的目标和当前实施计划。

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
