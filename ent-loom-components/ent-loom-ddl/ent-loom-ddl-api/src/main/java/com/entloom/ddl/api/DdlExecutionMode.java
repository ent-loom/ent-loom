package com.entloom.ddl.api;

/**
 * DDL 执行级别。
 */
public enum DdlExecutionMode {
    NONE,
    CREATE_TABLE,
    CREATE_TABLE_AND_METAS,
    CREATE_MODIFY_TABLE_AND_METAS,
    CREATE_MODIFY_DELETE_ALL
}
