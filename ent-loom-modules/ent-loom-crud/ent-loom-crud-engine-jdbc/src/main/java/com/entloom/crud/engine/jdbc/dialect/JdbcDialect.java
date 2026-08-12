package com.entloom.crud.engine.jdbc.dialect;

import java.util.List;

/**
 * JDBC 方言抽象，屏蔽分页与内置幂等表 DDL 差异。
 */
public interface JdbcDialect {
    /**
     * 追加分页查询 SQL（PAGE 场景）。
     */
    void appendPageClause(StringBuilder sql, int limit, int offset, List<Object> args);

    /**
     * 追加列表查询 SQL（LIST 场景）。
     */
    void appendListClause(StringBuilder sql, int limit, List<Object> args);

    /**
     * 追加可空单条查询 SQL（FIND_ONE 场景）。
     */
    void appendFindOneClause(StringBuilder sql, List<Object> args);

    /**
     * 追加详情查询 SQL（DETAIL 场景）。
     */
    void appendDetailClause(StringBuilder sql);

    /**
     * 生成幂等记录表建表语句。
     */
    String createIdempotencyTableSql(String tableName);

    /**
     * 判定异常是否为“表已存在”。
     */
    boolean isTableAlreadyExists(Throwable error);
}
