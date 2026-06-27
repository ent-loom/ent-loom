# Task / File 后续路线

> Status: Remaining
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

当前事实见 [Task / File 当前实现](../../architecture/components/crud/task-file.md)。

## 已实现

- `FileService` / `TaskService` 抽象。
- `LocalFileService` / `LocalTaskService` starter 默认实现。
- `InMemoryFileService` / `InMemoryTaskService` 测试实现。
- `TaskFileAccessGuard` 校验 task、subject、tenant、org、purpose、过期和文件元数据。

## 剩余事项

| 工作 | 当前原因 |
|---|---|
| 对象存储适配模板 | 当前 starter 默认本地目录实现 |
| 后台清理过期文件和任务 | 当前默认实现不提供统一清理 worker |
| streaming 读取写入接口 | 当前核心路径以 byte array 为主 |
| 任务进度事件 | 当前同步任务创建终态 task，不提供事件流 |
| 存储配额和租户隔离增强 | 当前只做基础大小、过期和访问校验 |
