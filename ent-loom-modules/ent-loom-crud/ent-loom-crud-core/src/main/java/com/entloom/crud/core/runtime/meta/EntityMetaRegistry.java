package com.entloom.crud.core.runtime.meta;

import java.util.Collection;

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
     * 返回已注册的实体元数据快照。
     *
     * <p>自定义注册表必须实现此方法并返回完整快照，供启动期数据库结构校验使用。
     * </p>
     *
     * @return 实体元数据完整快照
     * @throws UnsupportedOperationException 自定义注册表未提供完整快照
     */
    default Collection<EntityMeta> getEntityMetas() {
        throw new UnsupportedOperationException(
            "自定义 EntityMetaRegistry 必须实现 getEntityMetas() 以支持启动期数据库校验"
        );
    }

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
