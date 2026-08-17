# Meta 实施工作区

本目录是 Meta 裁决能力的当前实施闭环。规范性结论见 [Metadata Resolution Contract](../../architecture/core/meta/metadata-resolution-contract.md)，长期剩余目标见 [Meta 路线图](../../roadmap/meta/index.md)。

## 当前文档

1. [实施路线](metadata-resolution-roadmap.md)：描述从裁决契约到运行时闭环的阶段目标。
2. [阶段实施清单](metadata-resolution-phase-plan.md)：描述当前阶段、完成标志和固定验收项。
3. [轻量实施边界](metadata-resolution-lightweight-boundary.md)：约束每个切片的职责、抽象准入和停止条件。

## 小闭环

```text
规范性契约
  -> 实施路线
  -> 阶段清单
  -> 代码与测试验收
  -> 回写当前事实
```

工作区文档可以变化，但不得重新定义契约中的来源、优先级、模块边界和冲突处理规则。
