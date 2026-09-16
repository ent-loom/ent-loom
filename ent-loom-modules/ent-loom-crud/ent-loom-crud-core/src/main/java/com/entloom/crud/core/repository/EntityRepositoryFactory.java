package com.entloom.crud.core.repository;

/** 创建绑定实体类型的强类型仓储。 */
public interface EntityRepositoryFactory {
    <T, ID> EntityRepository<T, ID> repository(Class<T> entityType, Class<ID> idType);
}
