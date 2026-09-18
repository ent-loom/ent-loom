package com.entloom.crud.core.capability.dao;

import java.lang.reflect.Method;

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

    /**
     * 启动期校验 DAO 自定义方法。实现可以根据 JDBC 方言和实体元数据解析 SQL。
     *
     * @param entityType DAO 绑定的实体类型
     * @param method 自定义方法
     */
    default void validateCustomMethod(EntityType<?, ?> entityType, Method method) {
        throw new UnsupportedOperationException("当前 EntityDaoFactory 不支持 DAO 自定义方法: " + method);
    }

    /**
     * 在可信访问范围内执行 DAO 自定义方法。
     *
     * @param entityType DAO 绑定的实体类型
     * @param scope 本次调用的可信访问范围
     * @param method 自定义方法
     * @param args 方法参数
     * @return 方法结果
     */
    default Object invokeCustom(
        EntityType<?, ?> entityType,
        EntityAccessScope scope,
        Method method,
        Object[] args
    ) {
        throw new UnsupportedOperationException("当前 EntityDaoFactory 不支持 DAO 自定义方法: " + method);
    }
}
