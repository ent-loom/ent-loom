ent-loom-module下一级模块,不互相依赖:
ent-loom-crud
ent-loom-ddl
ent-loom-ui
...

每个一级模下,只允许:
ent-loom-crud
    ent-loom-crud-meta
ent-loom-ddl
    ent-loom-ddl-meta
ent-loom-ui
    ent-loom-ui-meta
...
依赖和扩展 ent-loom-meta (统一的元信息,减少业务90%,庞杂的配置)