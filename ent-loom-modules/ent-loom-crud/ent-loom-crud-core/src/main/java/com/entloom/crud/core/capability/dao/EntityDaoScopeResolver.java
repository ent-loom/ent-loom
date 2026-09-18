package com.entloom.crud.core.capability.dao;

/** 实体 DAO 的可信范围解析器；每次数据访问调用，不得缓存请求范围。 */
@FunctionalInterface
public interface EntityDaoScopeResolver {
    /** 根据实体及当前可信上下文解析范围；全量访问必须显式返回 unrestricted。 */
    EntityAccessScope resolve(EntityType<?, ?> entityType);
}
