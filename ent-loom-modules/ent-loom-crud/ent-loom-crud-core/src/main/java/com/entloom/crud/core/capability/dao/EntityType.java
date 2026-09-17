package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;

/**
 * 同时携带实体类型和主键类型的运行时描述符。
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
public final class EntityType<T, ID> {
    private final Class<T> entityClass;
    private final Class<ID> idClass;

    private EntityType(Class<T> entityClass, Class<ID> idClass) {
        if (entityClass == null || idClass == null) {
            throw new ValidationException("EntityType 的实体类型和主键类型不能为空");
        }
        this.entityClass = entityClass;
        this.idClass = idClass;
    }

    public static <T, ID> EntityType<T, ID> of(Class<T> entityClass, Class<ID> idClass) {
        return new EntityType<T, ID>(entityClass, idClass);
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public Class<ID> getIdClass() {
        return idClass;
    }

    public Class<T> entityClass() {
        return entityClass;
    }

    public Class<ID> idClass() {
        return idClass;
    }
}
