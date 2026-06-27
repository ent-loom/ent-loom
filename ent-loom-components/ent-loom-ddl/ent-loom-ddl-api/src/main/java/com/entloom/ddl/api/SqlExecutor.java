package com.entloom.ddl.api;

import java.util.List;

/**
 * SQL 执行器。
 */
public interface SqlExecutor {
    void execute(List<String> sqlStatements);
}
