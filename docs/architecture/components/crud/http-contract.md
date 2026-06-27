# HTTP Contract 当前实现

> Status: Current
> Verified: 2026-05-04
> Scope: `ent-loom-crud-spring-boot-starter`

本文记录 CRUD Starter 当前公开的 HTTP 入口和响应边界。HTTP 层只负责 DTO、上下文组装、Facade 调用和响应脱敏；执行语义由各 Gateway 承担。

## Base Path

默认 base path：

```text
/api/ent-crud
```

可通过 `entloom.crud.controller.base-path` 配置覆盖。

## Query / Command / Stats

Query、Command、Stats 使用各自 Controller / Facade / Assembler 组装 `QuerySpec`、`CommandSpec`、`StatsSpec`，进入对应 Gateway。

详细 Spec 和 routeKey 合同见 [Query/Command 协议与路由明细](query-command-contract.md)。

## Import

当前路径：

```text
POST /{entity}/import/validate
POST /{entity}/import/{scene}/validate
POST /{entity}/import/submit
POST /{entity}/import/{scene}/submit
POST /{entity}/import/status
POST /{entity}/import/tasks/{taskId}/status
POST /{entity}/import/error
POST /{entity}/import/tasks/{taskId}/errors/download
```

HTTP DTO 经 `CrudImportExportSpecAssembler` 转成 `ImportSpec`。响应由 `CrudImportExportResponseAssembler` 转成脱敏 `CrudImportData` / `CrudTaskData`，错误文件下载成功时返回二进制。

## Export

当前路径：

```text
POST /{entity}/export/preview
POST /{entity}/export/{scene}/preview
POST /{entity}/export/submit
POST /{entity}/export/{scene}/submit
POST /{entity}/export/status
POST /{entity}/export/tasks/{taskId}/status
POST /{entity}/export/download
POST /{entity}/export/tasks/{taskId}/download
```

HTTP DTO 经 `CrudImportExportSpecAssembler` 转成 `ExportSpec`。响应由 `CrudImportExportResponseAssembler` 转成脱敏 `CrudExportData` / `CrudTaskData`，下载成功时返回二进制。

## 响应边界

- HTTP 不直接长期暴露 `ImportResult`、`ExportResult`、`CrudTask` 或 `FileRef`。
- task 响应不暴露 `contextSnapshot`。
- file 响应不暴露 storageKey、本地路径、对象存储 key 或 checksum 原文。
- 下载成功返回二进制；下载前的失败由异常翻译层返回统一错误。

## 当前限制

- Starter 不提供通用文件上传入口。
- Import 需要业务先获得 `sourceFile.fileId`。
- 二进制响应开始写出后不能再切换为 JSON 错误，因此下载预检必须发生在打开文件流之前。
