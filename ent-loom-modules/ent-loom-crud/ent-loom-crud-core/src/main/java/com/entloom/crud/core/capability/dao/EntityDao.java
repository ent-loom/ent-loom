package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import java.util.Optional;

/**
 * 单实体、单表主键 CRUD 合同。
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
public interface EntityDao<T, ID> {
    /** 在绑定范围和逻辑未删除条件内按主键查询。 */
    Optional<T> findById(ID id);

    /** 新增显式主键实体。 */
    ID insert(T entity);

    /** 按主键执行局部更新；空 Patch 和无可写字段 Patch 必须拒绝。 */
    int updateById(ID id, UpdatePatch<T> patch);

    /** 按主键删除；配置逻辑删除时执行逻辑删除。 */
    int deleteById(ID id);
}
