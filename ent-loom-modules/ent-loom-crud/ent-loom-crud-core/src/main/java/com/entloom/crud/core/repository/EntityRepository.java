package com.entloom.crud.core.repository;

import com.entloom.crud.api.model.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 面向单实体、单表常用操作的强类型仓储。
 *
 * <p>所有操作必须经过 CRUD Gateway，不允许绕过治理、路由、审计和幂等主链。</p>
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
public interface EntityRepository<T, ID> {
    Optional<T> findById(ID id);

    T getRequired(ID id);

    List<T> findAll(EntityQuery<T> query);

    PageResult<T> findPage(EntityQuery<T> query);

    boolean existsById(ID id);

    long count(EntityQuery<T> query);

    ID insert(T entity);

    int updateById(ID id, EntityPatch<T> patch);

    int deleteById(ID id);

    List<ID> insertBatch(List<T> entities);

    int updateBatch(List<EntityUpdate<T, ID>> updates);

    int deleteBatchByIds(Collection<ID> ids);

    T save(T entity);

    List<T> saveBatch(List<T> entities);

    int delete(EntityQuery<T> query);

    int update(EntityQuery<T> query, EntityPatch<T> patch);
}
