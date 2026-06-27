# Import 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud`

Import 是 CRUD 的一等能力域，和 Query、Command、Stats、Export 平级。历史阶段方案已归档到 [Import 方案归档](../../../archive/crud/import-plan.md)。

## 主链

```mermaid
flowchart LR
    http["Import HTTP / Facade"]
    spec["ImportSpec"]
    gateway["ImportGatewayImpl"]
    pipeline["ExecutionPipeline"]
    governance["governImport"]
    route["SceneHandlerRegistry"]
    engine["DefaultImportEngine"]
    task["TaskService / FileService"]

    http --> spec --> gateway --> pipeline --> governance --> route --> engine --> task
```

`ImportGatewayImpl` 固定执行 normalize、govern、execute、audit。治理拒绝发生在 payload customizer、format registry 和文件读取之前。

## 当前能力

| 能力 | 当前实现 |
|---|---|
| 操作 | `VALIDATE`、`SUBMIT`；`STATUS`、`CANCEL`、`DOWNLOAD_ERROR` 由 gateway 处理任务和文件 |
| 格式 | 通过 `ImportFormatRegistry` 获取 parser 和 error writer |
| 默认格式实现 | Excel 模块注册 `excel-xlsx` |
| 文件输入 | 通过 `sourceFile.fileId` 读取 `FileService` 中的文件 |
| 校验 | 表头、字段白名单、关系字段、逻辑删除字段、非空字段、类型转换、更新主键要求 |
| 写入 | `SUBMIT` 且非 `VALIDATE_ONLY` 时走 `CommandEngine` 批量写命令 |
| 错误文件 | 校验失败时生成 `IMPORT_ERROR` 文件 |
| 任务 | 同步执行也创建终态 `CrudTask` |

## 治理边界

Import 使用独立 `IMPORT/*` operation。`DefaultCrudGovernanceService.governImport` 会执行主体、属性、校验、资源、权限、数据范围和 enrich，不复用普通 Command 权限隐式放行。

## 默认引擎边界

`DefaultImportEngine` 是单实体、小文件、同步导入引擎。它负责解析文件、校验行、生成错误文件和通过 `CommandEngine` 写入；Excel 模块只提供 parser / writer，不直接写库。

## 当前限制

- 默认 engine 不处理 `COMMIT`。
- 默认写入复用 `CommandEngine`，不是独立 import JDBC executor。
- HTTP 不负责文件上传，导入依赖已有 `sourceFile.fileId`。
- 默认链路面向小文件和同步执行，不提供后台 worker。
