package com.entloom.crud.core.capability.dao;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 消费逐目标授权上下文的只读批量 DAO 合同。
 *
 * <p>当前只开放按授权目标主键批量读取；批量写入、事务原子性和部分成功结果由后续适配器另行声明。</p>
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
public interface EntityDaoBatch<T, ID> {
    /** 返回按输入主键顺序保存的命中实体，未命中目标省略。 */
    List<T> findAllById(Collection<ID> ids);

    /** 返回按输入主键顺序索引的命中实体，未命中目标省略。 */
    Map<ID, T> findAllByIdAsMap(Collection<ID> ids);

    /**
     * 严格按授权目标集合读取并返回逐目标观测结果。
     *
     * <p>调用方必须提供与授权目标一一对应的主键；结果中的无数据和执行失败目标不会进入值 Map。</p>
     */
    EntityDaoBatchReadResult<T, ID> readById(Collection<ID> ids);

    /** 返回本批次绑定的授权目标顺序。 */
    List<String> getTargetIds();
}
