package com.entloom.crud.core.capability.query.engine;

import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.runtime.engine.EngineCapability;
import java.util.List;

/**
 * 默认查询引擎。
 */
public interface QueryEngine {
    /**
     * 当前查询引擎声明的能力。
     *
     * @return 引擎能力
     */
    default EngineCapability capability() {
        return EngineCapability.unknown(getClass().getName());
    }

    /**
     * 分页查询。
     *
     * @param spec 查询 spec
     * @param <R> 返回类型
     * @return 分页结果
     */
    <R> PageResult<R> page(QuerySpec<R> spec);

    /**
     * 列表查询。
     *
     * @param spec 查询 spec
     * @param <R> 返回类型
     * @return 列表
     */
    <R> List<R> list(QuerySpec<R> spec);

    /**
     * 可空唯一查询。
     *
     * <p>用于“允许不存在”的唯一查询；多条命中视为 {@code QUERY_NOT_UNIQUE}，不是“取第一条”。</p>
     *
     * @param spec 查询 spec
     * @param <R> 返回类型
     * @return 可空单条
     */
    default <R> R findOne(QuerySpec<R> spec) {
        return detail(spec);
    }

    /**
     * 必须存在的资源详情查询。
     *
     * <p>用于“必须存在”的资源详情；默认按 id 或等价唯一键查询。</p>
     *
     * @param spec 查询 spec
     * @param <R> 返回类型
     * @return 详情
     */
    <R> R detail(QuerySpec<R> spec);

}
