package com.entloom.crud.core.security;

import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import java.util.List;
import java.util.Map;

/**
 * 统一 SQL 执行门面。
 */
public interface GuardedSqlExecutor {
    /**
     * 查询多行。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     * @return 行数据
     */
    List<Map<String, Object>> queryForList(String sql, List<Object> args, CrudExecutionContext context);

    /**
     * 查询单行。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     * @return 单行
     */
    Map<String, Object> queryForMap(String sql, List<Object> args, CrudExecutionContext context);

    /**
     * 查询单值。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     * @return 值
     */
    Object queryForObject(String sql, List<Object> args, CrudExecutionContext context);

    /**
     * 执行更新。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     * @return 影响行数
     */
    int update(String sql, List<Object> args, CrudExecutionContext context);

    /**
     * 执行插入并返回数据库生成主键。
     * 默认实现仅执行插入，不回传主键；具体实现可覆盖以支持自增主键回填。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     * @return 生成主键，未获取到时返回 null
     */
    default Object insertAndReturnGeneratedKey(String sql, List<Object> args, CrudExecutionContext context) {
        update(sql, args, context);
        return null;
    }
}
