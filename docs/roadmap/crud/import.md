# Import 后续路线

> Status: Remaining
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

当前事实见 [Import 当前实现](../../architecture/components/crud/import.md)。历史方案全文见 [Import 方案归档](../../archive/crud/import-plan.md)。

## 已实现

- `ImportGatewayImpl` 接入 `ExecutionPipeline`。
- `governImport` 独立治理与审计。
- scene 路由与非空 scene miss fail-closed。
- `ImportFormatRegistry` 与 unsupported format fail-closed。
- 默认同步 `DefaultImportEngine`。
- 表头/行校验、错误文件、通过 `CommandEngine` 批量写入。
- Starter HTTP validate / submit / status / error download。
- Excel xlsx parser 和错误文件 writer 自动注册。

## 剩余事项

| 工作 | 当前原因 |
|---|---|
| `IMPORT/COMMIT` 默认实现 | `ImportGateway` 有入口，但默认 engine 当前不处理 `COMMIT` |
| 独立 Import JDBC executor | 当前导入写入复用 `CommandEngine`，不是独立导入写入计划执行器 |
| 文件上传入口或业务上传桥接模板 | 当前导入要求已有 `sourceFile.fileId` |
| 异步导入 worker | 当前默认 engine 是同步小文件闭环 |
| 大文件 streaming / channel | 当前 `FileService` 面向小文件和字节数组 |
