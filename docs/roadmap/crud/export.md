# Export 后续路线

> Status: Remaining
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

当前事实见 [Export 当前实现](../../architecture/components/crud/export.md)。历史方案全文见 [Export 方案归档](../../archive/crud/export-plan.md)。

## 已实现

- `ExportGatewayImpl` 接入 `ExecutionPipeline`。
- `governExport` 独立治理与审计。
- scene 路由与非空 scene miss fail-closed。
- `ExportFormatRegistry` 与 unsupported format fail-closed。
- 默认同步 `DefaultExportEngine`。
- 字段合同、展示值渲染、文件生成、下载预检。
- Starter HTTP preview / submit / status / download。
- Excel xlsx writer 自动注册。

## 剩余事项

| 工作 | 当前原因 |
|---|---|
| 异步导出 worker | 当前默认 engine 是同步小文件闭环 |
| 复杂报表引擎 | 当前导出复用 `QueryEngine`，不是报表平台 |
| 大文件 streaming / channel | 当前 `FileService` 面向小文件和字节数组 |
| 更多格式 | 当前默认只注册 `excel-xlsx` |
| 下载错误响应增强 | 二进制下载必须在写出前完成预检，复杂错误表达仍可继续增强 |
