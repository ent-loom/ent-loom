由于 ent-loom-meta 中能力范围广,没有固定标准,所以把ent-loom-meta-components解耦出来.避免影响 ent-loom-components 独立核心功能的组织.

`ent-loom-meta-components` 用于承接 `ent-loom-meta` 的注解信息，并适配到具体能力模块。

当前结构:

```text
ent-loom-meta-components
├── ent-loom-meta-ddl
└── ent-loom-meta-crud
```

定位:

1. `ent-loom-meta-ddl`: meta 信息到 ddl 执行层的适配。
2. `ent-loom-meta-crud`: meta 信息到 crud 执行层的适配。

依赖约定:

1. `ent-loom-meta-ddl` 依赖 `ent-loom-meta` + `ent-loom-ddl-core`。
2. `ent-loom-meta-crud` 依赖 `ent-loom-meta` + `ent-loom-crud-core`。
