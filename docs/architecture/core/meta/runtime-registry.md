# 运行时元数据注册表 (Runtime Meta Registry)

在应用启动阶段，`ent-loom` 会将静态的元数据描述符（Descriptor）转换为运行时可执行的模型。`EntityMetaRegistry` 是这一转换的核心产物。

## 1. 核心接口：`EntityMetaRegistry`

`EntityMetaRegistry` 存储了所有已注册实体的运行时信息，并提供高效的查询索引。

```java
public interface EntityMetaRegistry {
    EntityMeta getEntityMeta(Class<?> entityType);
    ResourceDescriptor getResourceDescriptor(Class<?> entityType);
    RelationGraph getRelationGraph(Class<?> rootType);
    void validateOrThrow();
}
```

## 2. 运行时模型：`EntityMeta`

`EntityMeta` 是 `EntEntityDescriptor` 在 CRUD 领域的运行时投影。它包含了执行 SQL、权限校验所需的所有信息：
- **资源路径**: 映射到 HTTP 路径的资源 code。
- **字段映射**: Java 字段与数据库列（或计算逻辑）的对应关系。
- **关系边 (RelationEdge)**: 描述实体间的一跳关系，用于 `ROOT_FIRST` 补数。
- **读写策略**: 该实体是否允许查询、新增、修改或删除。

## 3. 关系图：`RelationGraph`

为了支持复杂的跨表查询，注册表会根据实体的 `RelationEdge` 构建全量关系图。
- **预计算**: 框架在启动时会为每个根实体预先计算其可达的关系子图。
- **路径解析**: 在请求执行时，路由引擎通过 `RelationGraph` 快速解析 `expandRelations` 或 `entityClasses` 序列所代表的物理路径。

## 4. 注册与校验流程

1.  **收集**: 通过 Spring Bean 扫描或手动注册，收集所有的实体类。
2.  **解析**: 调用 `EntMetaParser` 获取 `Descriptor`。
3.  **适配 (Adapter)**: 调用 `MetaCrudAdapter` 将 `Descriptor` 投影为 `EntityMeta`。
4.  **合并 (Merger)**: 将来自 Meta 的信息与 `ent-loom-crud` 原生注解信息按优先级合并。
5.  **校验**: 执行 `validateOrThrow()`。检查所有关系的 `targetEntity` 是否真实存在，字段引用是否正确。
6.  **冻结**: 注册表一旦校验通过，其内容应视为只读，以保证运行时的并发安全。

## 5. 性能设计

- **并发安全**: 内部使用 `ConcurrentHashMap` 或在启动后变为不可变集合。
- **$O(1)$ 查询**: 所有通过 `Class` 或 `resourceCode` 的查询均应在常数时间内完成，不应在运行时重新执行反射。
