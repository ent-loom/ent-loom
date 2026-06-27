@EntEntity
@EntField
@EntIndex
@EntIndexes
@EntMeta

面向业务和扩展的注解方案

## 模块定位

`ent-loom-meta` 是 Meta 体系聚合模块和文档入口，不再作为业务代码依赖入口。

子模块：

- `ent-loom-meta-contract`：解析后的 Descriptor 接口、共享枚举和跨模块语义契约。
- `ent-loom-meta-annotations`：`@EntEntity`、`@EntField`、`@EntRelation` 等轻薄母框架注解。
- `ent-loom-meta-core`：反射解析 Meta 注解，输出统一 Descriptor 模型。

业务或子框架按职责显式依赖具体子模块，不依赖聚合模块。

设计概要见：

- [Meta 注解架构设计概要](../docs/architecture/core/meta/annotation-architecture.md)
- [Ent Loom 注解分层与适配提要](../docs/architecture/core/meta/layering-summary.md)
- [Ent Loom Meta-first 最佳实践](../docs/guides/meta/meta-first.md)
- [Meta 后续路线](../docs/roadmap/meta/business-todo.md)：DDL、UI、包扫描、API 文档和运行期动态刷新等后续增强。
