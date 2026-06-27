package com.entloom.crud.core.capability.query;

import com.entloom.crud.api.model.PageResult;
import java.util.List;

/**
 * 查询执行器。
 */
public interface QueryExecutor {
    /**
     * 执行分页查询。
     *
     * @param query 编译 SQL
     * @param viewType 返回类型
     * @param <R> 返回类型
     * @return 分页结果
     */
    <R> PageResult<R> executePage(CompiledQuery query, Class<R> viewType);

    /**
     * 执行列表查询。
     *
     * @param query 编译 SQL
     * @param viewType 返回类型
     * @param <R> 返回类型
     * @return 列表结果
     */
    <R> List<R> executeList(CompiledQuery query, Class<R> viewType);

    /**
     * 执行可空单条查询（0/1 条）。
     *
     * @param query 编译 SQL
     * @param viewType 返回类型
     * @param <R> 返回类型
     * @return 可空单条
     */
    <R> R executeFindOne(CompiledQuery query, Class<R> viewType);

    /**
     * 执行必须单条查询（exactly one）。
     *
     * @param query 编译 SQL
     * @param viewType 返回类型
     * @param <R> 返回类型
     * @return 单条结果
     */
    <R> R executeOne(CompiledQuery query, Class<R> viewType);

    /**
     * 执行计数查询。
     *
     * @param query 编译 SQL
     * @return 总数
     */
    long executeCount(CompiledQuery query);
}
