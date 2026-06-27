package com.entloom.crud.core.capability.query.handler;

import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;

/**
 * 查询处理器。
 *
 * @param <R> 返回类型
 */
public interface QueryHandler<R> {
    /**
     * 是否支持当前请求。
     *
     * @param spec 查询 spec
     * @return true 表示支持
     */
    boolean supports(QuerySpec<R> spec);

    /**
     * 执行分页。
     *
     * @param spec 查询 spec
     * @return 分页结果
     */
    PageResult<R> page(QuerySpec<R> spec);

    /**
     * 执行列表查询。
     *
     * @param spec 查询 spec
     * @return 列表
     */
    List<R> list(QuerySpec<R> spec);

    /**
     * 执行可空唯一查询。
     *
     * <p>用于“允许不存在”的唯一查询；多条命中视为 {@code QUERY_NOT_UNIQUE}，不是“取第一条”。</p>
     *
     * @param spec 查询 spec
     * @return 可空单条
     */
    default R findOne(QuerySpec<R> spec) {
        return detail(spec);
    }

    /**
     * 执行必须存在的资源详情查询。
     *
     * <p>用于“必须存在”的资源详情；默认按 id 或等价唯一键查询。</p>
     *
     * @param spec 查询 spec
     * @return 详情
     */
    R detail(QuerySpec<R> spec);

}
