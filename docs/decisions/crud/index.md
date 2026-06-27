# CRUD 设计决策

本目录记录 CRUD 相关的重大设计取舍、破坏式重构建议和阶段性定稿记录。

## 决策记录

- [Capability / Operation 重构式设计方案](capability-operation-refactor.md)
- [Command 包破坏式重构建议](command-package-refactor.md)
- [小版本重构最佳实践建议](minor-version-refactor.md)
- [强类型命令 Handler 重构定稿记录](typed-command-handler-conclusions.md)

## 文档边界

- 当前公共契约不放在这里，放在 `docs/architecture/components/crud/`。
- 待办和阶段推进不放在这里，放在 `docs/roadmap/crud/`。
- 这里只回答“为什么曾经这样定”和“后续重构应遵守哪些设计取舍”。
