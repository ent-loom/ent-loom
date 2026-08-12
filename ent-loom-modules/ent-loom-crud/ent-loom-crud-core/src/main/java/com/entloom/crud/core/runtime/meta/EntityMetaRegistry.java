package com.entloom.crud.core.runtime.meta;

/**
 * 元数据注册表。
 */
public interface EntityMetaRegistry {
    /**
     * 查询实体元数据。
     *
     * @param entityType 实体类型
     * @return 元数据
     */
    EntityMeta getEntityMeta(Class<?> entityType);

    /**
     * 查询实体对应的资源描述。
     *
     * @param entityType 实体类型
     * @return 资源描述
     */
    ResourceDescriptor getResourceDescriptor(Class<?> entityType);

    /**
     * 查询关系图。
     *
     * @param rootType 根实体
     * @return 关系图
     */
    RelationGraph getRelationGraph(Class<?> rootType);

    /**
     * 启动期校验。
     */
    void validateOrThrow();
}
