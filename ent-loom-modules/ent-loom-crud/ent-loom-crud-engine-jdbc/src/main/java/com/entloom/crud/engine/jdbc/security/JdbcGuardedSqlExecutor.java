package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.engine.jdbc.log.SqlExecutionLogger;
import com.entloom.crud.core.runtime.context.CrudExecutionContext;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * JDBC 的统一 SQL 执行门面。
 */
@RequiredArgsConstructor
public class JdbcGuardedSqlExecutor implements GuardedSqlExecutor {
    /** JDBC 模板。 */
    private final JdbcTemplate jdbcTemplate;
    /** SQL 安全守卫。 */
    private final SqlSecurityGuard sqlSecurityGuard;
    /** SQL 执行日志器。 */
    private final SqlExecutionLogger sqlExecutionLogger;

    /**
     * 执行受保护的列表查询并记录日志。
     */
    @Override
    public List<Map<String, Object>> queryForList(String sql, List<Object> args, CrudExecutionContext context) {
        long start = System.currentTimeMillis();
        List<Object> safeArgs = args == null ? Collections.emptyList() : args;
        sqlSecurityGuard.beforeExecute(sql, safeArgs, context);
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, safeArgs.toArray());
            sqlExecutionLogger.logSql(context, op(context), phase(context), sql, safeArgs, result.size(), System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            sqlExecutionLogger.logSqlFailure(context, op(context), phase(context), sql, safeArgs, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    /**
     * 执行受保护的单条映射查询并记录日志。
     */
    @Override
    public Map<String, Object> queryForMap(String sql, List<Object> args, CrudExecutionContext context) {
        long start = System.currentTimeMillis();
        List<Object> safeArgs = args == null ? Collections.emptyList() : args;
        sqlSecurityGuard.beforeExecute(sql, safeArgs, context);
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, safeArgs.toArray());
            sqlExecutionLogger.logSql(context, op(context), phase(context), sql, safeArgs, 1, System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            sqlExecutionLogger.logSqlFailure(context, op(context), phase(context), sql, safeArgs, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    /**
     * 执行受保护的单值查询并记录日志。
     */
    @Override
    public Object queryForObject(String sql, List<Object> args, CrudExecutionContext context) {
        long start = System.currentTimeMillis();
        List<Object> safeArgs = args == null ? Collections.emptyList() : args;
        sqlSecurityGuard.beforeExecute(sql, safeArgs, context);
        try {
            Object result = jdbcTemplate.queryForObject(sql, Object.class, safeArgs.toArray());
            sqlExecutionLogger.logSql(context, op(context), phase(context), sql, safeArgs, 1, System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            sqlExecutionLogger.logSqlFailure(context, op(context), phase(context), sql, safeArgs, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    /**
     * 执行更新操作。
     */
    @Override
    public int update(String sql, List<Object> args, CrudExecutionContext context) {
        long start = System.currentTimeMillis();
        List<Object> safeArgs = args == null ? Collections.emptyList() : args;
        sqlSecurityGuard.beforeExecute(sql, safeArgs, context);
        try {
            int rows = jdbcTemplate.update(sql, safeArgs.toArray());
            sqlExecutionLogger.logSql(context, op(context), phase(context), sql, safeArgs, rows, System.currentTimeMillis() - start);
            return rows;
        } catch (RuntimeException ex) {
            sqlExecutionLogger.logSqlFailure(context, op(context), phase(context), sql, safeArgs, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    /**
     * 执行插入并返回数据库生成主键。
     */
    @Override
    public Object insertAndReturnGeneratedKey(String sql, List<Object> args, CrudExecutionContext context) {
        long start = System.currentTimeMillis();
        List<Object> safeArgs = args == null ? Collections.emptyList() : args;
        sqlSecurityGuard.beforeExecute(sql, safeArgs, context);
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            int rows = jdbcTemplate.update(connection -> {
                PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < safeArgs.size(); i++) {
                    preparedStatement.setObject(i + 1, safeArgs.get(i));
                }
                return preparedStatement;
            }, keyHolder);
            sqlExecutionLogger.logSql(context, op(context), phase(context), sql, safeArgs, rows, System.currentTimeMillis() - start);
            Number generated = keyHolder.getKey();
            if (generated != null) {
                return generated;
            }
            Map<String, Object> keys = keyHolder.getKeys();
            if (keys == null || keys.isEmpty()) {
                return null;
            }
            for (Object value : keys.values()) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        } catch (RuntimeException ex) {
            sqlExecutionLogger.logSqlFailure(context, op(context), phase(context), sql, safeArgs, System.currentTimeMillis() - start, ex);
            throw ex;
        }
    }

    private String op(CrudExecutionContext context) {
        Object operationDomain = context.getAttributes().get("operationDomain");
        Object operation = context.getAttributes().get("operation");
        if (operationDomain != null && operation != null) {
            return String.valueOf(operationDomain) + "/" + operation;
        }
        Object value = context.getAttributes().get("op");
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }

    private String phase(CrudExecutionContext context) {
        Object value = context.getAttributes().get("phase");
        return value == null ? "main" : String.valueOf(value);
    }
}
