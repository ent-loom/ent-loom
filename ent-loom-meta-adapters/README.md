由于 ent-loom-meta 中能力范围广,没有固定标准,所以把 ent-loom-meta-adapters 解耦出来，避免影响 ent-loom-components 独立核心功能的组织。

`ent-loom-meta-adapters` 用于承接 `ent-loom-meta-core` 解析后的 Descriptor，并适配到具体能力模块。

当前结构:

```text
ent-loom-meta-adapters
├── ent-loom-meta-adapter-crud
├── ent-loom-meta-adapter-ddl
├── ent-loom-meta-adapter-doc
└── ent-loom-meta-spring-boot-starter
```

定位:

1. `ent-loom-meta-adapter-crud`: Meta Descriptor 到 CRUD 运行时模型的适配；P0 已覆盖 Meta-only、CRUD-only、Meta + CRUD override、关系方向和诊断。
2. `ent-loom-meta-adapter-ddl`: Meta Descriptor 到 DDL 执行层的适配。
3. `ent-loom-meta-adapter-doc`: Meta Descriptor 到 DOC 输出模型的适配；P0 已覆盖 Meta-only、DOC-only、Meta + DOC override、稳定 DOC Runtime Model、关系/索引文档和诊断。
4. `ent-loom-meta-spring-boot-starter`: P1 装配层，只负责条件注册 `MetaCrudAdapter` / `MetaDocAdapter`，不承载合并规则。

依赖约定:

1. adapter 统一依赖 `ent-loom-meta-core`。
2. adapter 只依赖目标能力模块的 core，不反向污染目标模块。
3. `ent-loom-components` 中的 CRUD/DOC/DDL 保持独立，不直接依赖 `ent-loom-meta-annotations`。
4. starter 可依赖 adapter、目标 core 和 Spring Boot auto-config；core 模块不能反向依赖 starter。

P0 验收入口:

- `ent-loom-meta-adapter-crud/src/test/java/com/entloom/meta/adapter/crud/MetaCrudAdapterP0AcceptanceTest.java`
- `ent-loom-meta-adapter-doc/src/test/java/com/entloom/meta/adapter/doc/MetaDocAdapterP0AcceptanceTest.java`

P1-1 验收入口:

- `ent-loom-meta-spring-boot-starter/src/test/java/com/entloom/meta/starter/EntLoomMetaAutoConfigurationTest.java`
- `ent-loom-meta-spring-boot-starter/src/test/java/com/entloom/meta/starter/EntLoomMetaStarterBoundaryGuardTest.java`
