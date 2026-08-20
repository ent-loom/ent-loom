# 设计决策

> 状态：Current

Decision 记录已经接受或被替代的关键取舍，只解释背景、结论和后果。当前 API 与运行行为以 [architecture](../../architecture/index.md) 为准。

- [Core 决策](core/index.md)
- [CRUD 决策](crud/index.md)

## 状态规则

| 状态 | 含义 |
|---|---|
| `Proposed` | 尚未接受或实现，只用于评审 |
| `Accepted` | 结论仍有效，可能已实现或正在实施 |
| `Superseded` | 核心结论已被新决策替代 |
| `Partially Superseded` | 部分原则仍有效，但指定结论已失效 |

Proposed 文档不得被 Architecture 或 Guide 当作当前能力。Decision 不保存当前任务队列、完整迁移步骤或重复的 API 文档。
