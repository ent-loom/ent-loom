# Ent-Loom 组件边界与协作规约

本文定义 `ent-loom` 框架内各个核心组件（CRUD, DOC, DDL, Meta）之间的职责边界、交互方式以及解耦原则。

## 1. 核心组件定位

| 组件 | 核心职责 | 核心模型 |
| :--- | :--- | :--- |
| **ent-loom-meta** | 元数据定义与注册中心。提供基础语义（实体、字段、关系、索引）。 | `EntityMeta`, `FieldMeta` |
| **ent-loom-crud** | 运行时数据操作网关。负责路由分发、权限治理、数据范围控制及执行链。 | `QuerySpec`, `CommandSpec` |
| **ent-loom-doc** | 文档服务层。负责文档元数据建模、组装与查询视图，提供人类可读的协议说明。 | `DocEntityModel`, `DocActionModel` |
| **ent-loom-ddl** | 数据库结构治理。负责表结构同步、索引维护与 Schema 演进。 | `TableDefinition`, `ColumnDefinition` |

## 2. 职责边界原则

### 2.1 ent-loom-doc 与 ent-loom-crud
- **单向可见性**：`ent-loom-doc` 描述业务能力，但不依赖 `ent-loom-crud` 的执行逻辑。
- **元数据共享**：两者共同依赖 `ent-loom-meta` 提供的基础结构定义，但各自拥有独立的视图模型。
- **路由解耦**：`ent-loom-crud` 负责“如何调（Routing）”，`ent-loom-doc` 负责“调什么（Documentation）”。`doc` 不应包含具体的 Controller 路径映射或执行拦截器。

### 2.2 跨组件交互 (Adapters)
组件间的强解耦通过 `ent-loom-meta-adapters` 实现：
- **CRUD Adapter**：将业务代码中的 CRUD 注解转换为框架可识别的 `Spec` 和路由。
- **Doc Adapter**：将 CRUD 路由元数据或业务 API 注解转换为 `DocActionDefinition`。

## 3. 协作矩阵

| 交互场景 | 主导组件 | 辅助组件 | 协作方式 |
| :--- | :--- | :--- | :--- |
| 自动生成 API 文档 | `ent-loom-doc` | `ent-loom-crud` | 通过 Doc Adapter 提取 CRUD 路由信息并渲染。 |
| 基于文档的参数校验 | `ent-loom-doc` | `ent-loom-base` | 复用 `DocParam` 定义的约束语义。 |
| 数据字典导出 | `ent-loom-doc` | `ent-loom-meta` | 直接读取 Meta 注册表进行文档化输出。 |

## 4. 演进约束
1. **禁止反向依赖**：文档模块不能承接任何业务执行逻辑。
2. **禁止语义污染**：不要为了展示目的（如“分组”、“隐藏”）去修改 Meta 层的核心语义，应使用 `DocOverrideProvider`。
3. **保持原子性**：各组件应能脱离其他组件独立运行（如 DDL 可在没有 CRUD 的环境下工作）。
