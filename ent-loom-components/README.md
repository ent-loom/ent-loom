`ent-loom-components` 为能力层聚合模块，一级模块之间不互相依赖。

当前结构:

```text
ent-loom-components
├── ent-loom-ddl
├── ent-loom-crud
└── ent-loom-ui
```

```text
└─ ent-loom-ddl (pom)
   ├─ ent-loom-ddl-api
   │  └─ (接口/枚举/元数据模型，如 MetadataLoader、QueryStrategy 等)
   ├─ ent-loom-ddl-annotations
   │  └─ depends on: ent-loom-ddl-api
   ├─ ent-loom-ddl-core
   │  └─ depends on: ent-loom-ddl-api
   ├─ ent-loom-ddl-bootstrap   (无 Spring 默认启动器)
   │  └─ depends on: core + annotations (+ classpath 扫描库)
   ├─ ent-loom-ddl-spring      (Spring 适配器)
   │  └─ depends on: core + annotations + spring-context
   └─ ent-loom-ddl-spring-boot-starter
      └─ depends on: ent-loom-ddl-spring + spring-boot-autoconfigure
```
约定:

1. 这里只放“能力模块”骨架与实现。
2. `meta` 到能力层的适配放在 `ent-loom-meta-components`，不放在这里。
