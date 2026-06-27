# Task / File 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

Task / File 是 Import 和 Export 共享的基础能力。它放在 CRUD core 的 `foundation.taskfile` 下，负责小文件、任务状态和下载预检，不绑定 Excel 或 HTTP。

## 核心类型

| 类型 | 职责 |
|---|---|
| `FileService` | 保存、读取、按 fileId 获取文件 |
| `TaskService` | 创建、查询、取消任务 |
| `FileRef` | 文件引用，包含 fileId、文件名、contentType、size、expiresAt 和 attributes |
| `CrudTask` | 任务状态、结果文件、错误文件、进度、上下文快照 |
| `CrudTaskContextSnapshot` | 记录 rootType、scene、operation、subject 等任务归属上下文 |
| `TaskFileAccessGuard` | 校验 task 归属、主体、purpose、过期和文件元数据 |

## 默认实现

Starter 默认提供：

- `LocalFileService`：本地目录文件服务。
- `LocalTaskService`：本地目录任务服务。
- `TaskFileAccessGuard`：下载和任务访问守卫。

Core 测试使用：

- `InMemoryFileService`
- `InMemoryTaskService`

## 下载预检

下载前必须完成：

1. task 存在。
2. task 的 rootType 和 scene 与请求匹配。
3. subject、tenant、org 与 task 快照匹配。
4. 文件 purpose 匹配，例如 `IMPORT_ERROR` 或 `EXPORT_RESULT`。
5. 文件未过期。
6. 文件名、contentType、size、format 元数据完整。

这些规则由 `TaskFileAccessGuard` 执行。Gateway 在 `download` / `downloadError` 路径上调用它。

## 当前限制

- 默认实现面向小文件。
- 文件读取以 byte array 为主。
- 对象存储、streaming、后台清理和任务 worker 由业务替换或后续扩展。
