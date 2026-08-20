# CRUD Runtime Registry

> 状态：Current
> 最近核验：2026-08-20

`EntityMetaRegistry` 是 CRUD Runtime Model 的只读查询入口。它属于 CRUD，不是通用 Meta Registry。

## 构建主链

```text
CRUD Native Input + optional Meta Adapter Input
  -> CrudRuntimeModel
  -> validate resources / fields / relations
  -> build indexes and relation graphs
  -> CrudRuntimeModelBackedEntityMetaRegistry
```

Spring 默认装配收集 `ResourceCatalogAdapter.runtimeModel()`，合并后构建唯一 Registry。Meta-first、CRUD-only 和 Meta + CRUD 都汇聚到同一种 `CrudRuntimeModel`。

## 主要查询

```java
public interface EntityMetaRegistry {
    EntityMeta getEntityMeta(Class<?> entityType);
    ResourceDescriptor getResourceDescriptor(Class<?> entityType);
    RelationGraph getRelationGraph(Class<?> rootType);
    void validateOrThrow();
}
```

Registry 提供实体、resourceCode/alias、字段/列和关系图索引。运行时不重新反射解释实体，也不暴露注册方法。

## 冻结规则

- 构造期完成重复 resourceCode、字段引用和关系目标校验。
- 内部集合对外只读。
- 每个 root 的关系图在启动期预计算。
- 运行期请求中的 entityCodes 只能选择已注册实体，不能动态注册新实体。
- 路径歧义和缺失在进入 SQL 编译前失败。

## 模型边界

`EntityMeta`、`ResourceDescriptor`、`RelationEdge` 和 `RelationGraph` 都由 CRUD Core 拥有。Meta Descriptor 只能通过 Adapter 投影，不直接成为查询或写入引擎的合同。

## 变更门禁

新增模型入口或 Registry 实现时，必须覆盖 CRUD-only、Meta-only、Meta + CRUD、重复资源、关系目标缺失和冻结集合测试。出现第二套运行期 Registry 属于架构违规。
