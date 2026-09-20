package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import java.util.Collection;
import java.util.List;
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

    /** 按输入 ID 首次出现的顺序查询；未命中的 ID 省略。 */
    List<T> findAllById(Collection<ID> ids);

    /** 新增实体；显式主键直接写入，数据库生成主键则回填到实体后返回。 */
    ID insert(T entity);

    /** 按输入实体顺序批量新增，并回填数据库生成的主键。 */
    List<ID> insertAll(Collection<T> entities);

    /** 按实体主键执行完整可写状态更新，null 表示清空。 */
    int update(T entity);

    /** 按实体主键执行完整可写状态更新，语义与 {@link #update(Object)} 相同。 */
    int updateById(T entity);

    /** 按主键执行局部更新；空 Patch 和无可写字段 Patch 必须拒绝。 */
    int updateById(ID id, UpdatePatch<T> patch);

    /** 按输入实体顺序批量更新。 */
    int updateAll(Collection<T> entities);

    /** 删除实体；启用版本控制时使用实体中的预期版本。 */
    int delete(T entity);

    /** 按主键删除；未启用版本控制时删除不存在记录返回 0。 */
    int deleteById(ID id);

    /** 按主键和预期版本删除；版本不匹配抛并发写入失败异常。 */
    int deleteById(ID id, long expectedVersion);

    /** 按输入实体顺序批量删除。 */
    int deleteAll(Collection<T> entities);
}
