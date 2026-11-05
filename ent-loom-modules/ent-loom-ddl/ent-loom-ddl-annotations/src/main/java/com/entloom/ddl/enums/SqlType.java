package com.entloom.ddl.enums;

/**
 * SQL 类型提示。
 */
public enum SqlType {
    AUTO,
    VARCHAR,
    CHAR,
    TEXT,
    /** 中等容量文本，最多 16777215 字节。 */
    MEDIUMTEXT,
    LONGTEXT,
    INT,
    BIGINT,
    DECIMAL,
    DOUBLE,
    BOOLEAN,
    DATE,
    DATETIME,
    TIMESTAMP,
    JSON,
    BLOB
}
