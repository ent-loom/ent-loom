package com.entloom.crud.core.capability.query.gateway;

import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.capability.dao.EntityType;
import java.util.List;

/**
 * 查询网关。
 */
public interface QueryGateway {
    /**
     * 分页查询。
     *
     * @param spec 查询协议
     * @param <R> 返回类型
     * @return 分页结果
     */
    <R> PageResult<R> page(QuerySpec<R> spec);

    /**
     * 列表查询。
     *
     * @param spec 查询协议
     * @param <R> 返回类型
     * @return 列表结果
     */
    <R> List<R> list(QuerySpec<R> spec);

    /**
     * 可空唯一查询。
     *
     * <p>用于“允许不存在”的唯一查询；多条命中视为 {@code QUERY_NOT_UNIQUE}，不是“取第一条”。</p>
     *
     * @param spec 查询协议
     * @param <R> 返回类型
     * @return 单条对象（可为空）
     */
    <R> R findOne(QuerySpec<R> spec);

    /**
     * 必须存在的资源详情查询。
     *
     * <p>用于“必须存在”的资源详情；默认按 id 或等价唯一键查询，不是“取第一条”。</p>
     *
     * @param spec 查询协议
     * @param <R> 返回类型
     * @return 详情对象
     */
    <R> R detail(QuerySpec<R> spec);

    /**
     * 按实体主键获取必须存在的详情，主键字段由实体元数据解析。
     *
     * <p>复用详情查询的授权、数据范围和审计链路；不存在或不可见时沿用详情查询语义。</p>
     *
     * @param type 实体与主键类型
     * @param id 非空主键
     * @param <T> 实体类型
     * @param <ID> 主键类型
     * @return 实体详情
     */
    <T, ID> T detail(EntityType<T, ID> type, ID id);

}
