package com.entloom.ddl.api;

/**
 * DDL 引擎入口。
 */
public interface DdlEngine {
    DdlExecutionResult execute(DdlExecutionRequest request, QueryStrategy queryStrategy, SqlExecutor sqlExecutor);
}
