package com.entloom.crud.core.capability.dao;

/**
 * 创建绑定实体和访问范围的 DAO。
 */
public interface EntityDaoFactory {
    /**
     * 创建指定实体和不可变访问范围下的 DAO。
     *
     * @param entityType 实体及主键类型描述
     * @param scope 可信应用层确定的访问范围
     * @param <T> 实体类型
     * @param <ID> 主键类型
     * @return 绑定范围的 DAO
     */
    <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope);
}
