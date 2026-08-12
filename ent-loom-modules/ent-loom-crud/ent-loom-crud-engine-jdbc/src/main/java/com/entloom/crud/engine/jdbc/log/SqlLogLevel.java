package com.entloom.crud.engine.jdbc.log;

/**
 * SQL 日志输出模式。
 */
public enum SqlLogLevel {
    /** 安全模式：不打印参数值。 */
    SAFE,
    /** 全量模式：打印参数值。 */
    FULL
}
