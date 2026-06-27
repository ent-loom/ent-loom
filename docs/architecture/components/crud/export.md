# Export 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

Export 是 CRUD 的一等能力域，和 Query、Command、Stats、Import 平级。历史阶段方案已归档到 [Export 方案归档](../../../archive/crud/export-plan.md)。

## 主链

```mermaid
flowchart LR
    http["Export HTTP / Facade"]
    spec["ExportSpec"]
    gateway["ExportGatewayImpl"]
    pipeline["ExecutionPipeline"]
    governance["governExport"]
    route["SceneHandlerRegistry"]
    engine["DefaultExportEngine"]
    task["TaskService / FileService"]

    http --> spec --> gateway --> pipeline --> governance --> route --> engine --> task
```

`ExportGatewayImpl` 固定执行 normalize、govern、execute、audit。下载通过 gateway 重新校验 task、主体、状态、purpose 和文件元数据。

## 当前能力

| 能力 | 当前实现 |
|---|---|
| 操作 | `PREVIEW`、`SUBMIT`；`STATUS`、`CANCEL`、`DOWNLOAD` 由 gateway 处理任务和文件 |
| 格式 | 通过 `ExportFormatRegistry` 获取 writer |
| 默认格式实现 | Excel 模块注册 `excel-xlsx` |
| 读取 | 通过 `QueryEngine` 构造 `QuerySpec` 读取实体数据 |
| 列解析 | `DefaultExportColumnResolver` 解析字段白名单、展示列和关系展示列 |
| 展示值 | `DefaultExportValueRenderer` 处理枚举、布尔、时间和字典展示 |
| 文件生成 | `SUBMIT` 写入 `EXPORT_RESULT` 文件 |
| 任务 | `SUBMIT` 创建终态 `CrudTask` |

## 治理边界

Export 使用独立 `EXPORT/*` operation。`DefaultCrudGovernanceService.governExport` 会执行主体、属性、校验、资源、权限、数据范围和 enrich，不复用普通 Query 权限隐式放行。

## 默认引擎边界

`DefaultExportEngine` 是单实体、小文件、同步导出引擎。它复用 `QueryEngine` 读取数据，按导出列合同生成 preview 或文件；它不是复杂报表引擎。

## 当前限制

- 默认链路不提供后台 worker。
- 默认导出读取复用 `QueryEngine`，不是报表平台。
- 默认格式只有 `excel-xlsx`。
- 大文件 streaming / channel 仍属于后续增强。
