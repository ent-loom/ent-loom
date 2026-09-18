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
     * 默认实现执行前拒绝；支持此能力的实现必须检查影响一行且返回唯一非空生成键。
     *
     * @param sql SQL 模板
     * @param args 参数列表
     * @param context 执行上下文
     * @return 唯一非空生成主键
     * @throws UnsupportedOperationException 当前执行器未实现生成键能力，且尚未执行 SQL
     */
    default Object insertAndReturnGeneratedKey(String sql, List<Object> args, CrudExecutionContext context) {
        throw new UnsupportedOperationException("当前 SQL 执行器不支持数据库生成主键");
    }
}
