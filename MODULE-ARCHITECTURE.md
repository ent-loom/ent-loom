# ent-loom 模块梳理（2026-04-06）

基于当前仓库目录与 `pom.xml` 汇总。

## 1. 当前聚合层级（Maven modules）

```mermaid
flowchart TD
    root["ent-loom (parent)"]
    base["ent-loom-base"]
    meta["ent-loom-meta"]
    comp["ent-loom-components (aggregator)"]
    meta_comp["ent-loom-meta-components (aggregator)"]

    root --> base
    root --> meta
    root --> comp
    root --> meta_comp

    ddl["ent-loom-ddl (aggregator)"]
    crud["ent-loom-crud (aggregator)"]
    ui["ent-loom-ui (aggregator)"]
    comp --> ddl
    comp --> crud
    comp --> ui

    ddl_ann["ent-loom-ddl-annotations"]
    ddl_core["ent-loom-ddl-core"]
    ddl --> ddl_ann
    ddl --> ddl_core

    crud_core["ent-loom-crud-core"]
    crud --> crud_core

    ui_core["ent-loom-ui-core"]
    ui --> ui_core

    meta_ddl["ent-loom-meta-ddl"]
    meta_crud["ent-loom-meta-crud"]
    meta_comp --> meta_ddl
    meta_comp --> meta_crud
```

## 2. 当前模块状态

| 模块 | 状态 | 说明 |
|---|---|---|
| `ent-loom-base` | implemented | 基础工具与公共类型 |
| `ent-loom-meta` | implemented | 实体元信息注解 |
| `ent-loom-ddl-annotations` | implemented | DDL 注解层 |
| `ent-loom-ddl-core` | scaffold | DDL 执行层骨架 |
| `ent-loom-crud-core` | implemented | CRUD 核心契约（Provider + Contract DTO） |
| `ent-loom-ui-core` | implemented | UI 核心契约（Provider + Contract DTO） |
| `ent-loom-meta-ddl` | scaffold | meta 到 ddl 适配层骨架 |
| `ent-loom-meta-crud` | scaffold | meta 到 crud 适配层骨架 |

## 3. 当前依赖方向（以 POM 依赖为准）

```mermaid
flowchart LR
    base["ent-loom-base"]
    meta["ent-loom-meta"]
    ddl_ann["ent-loom-ddl-annotations"]
    ddl_core["ent-loom-ddl-core"]
    crud_core["ent-loom-crud-core"]
    meta_ddl["ent-loom-meta-ddl"]
    meta_crud["ent-loom-meta-crud"]

    meta --> base
    ddl_ann --> base
    ddl_core --> base

    meta_ddl --> meta
    meta_ddl --> ddl_core

    meta_crud --> meta
    meta_crud --> crud_core
```

## 4. 命名与边界约定

1. `ent-loom-components`: 放置纯能力组件聚合（ddl/crud/ui）。
2. `ent-loom-meta-components`: 放置 `meta -> 具体能力` 的适配层。
3. `*-core` 模块承载跨层可复用契约，不混入实现细节。
4. 根 `dependencyManagement` 只维护真实存在的 artifactId。
