package com.entloom.base.util.value;

/**
 * 通用字符串值的目标解析类型。
 */
public enum TypedValueType {
    /** 未显式设置。 */
    UNSET,
    /** 字符串。 */
    STRING,
    /** 布尔。 */
    BOOLEAN,
    /** 32 位整数。 */
    INT,
    /** 64 位整数。 */
    LONG,
    /** 高精度小数（BigDecimal 语义）。 */
    DECIMAL,
    /** JSON 文本。 */
    JSON,
    /** ISO-8601 本地日期（yyyy-MM-dd）。 */
    ISO_LOCAL_DATE,
    /** ISO-8601 本地日期时间（yyyy-MM-dd'T'HH:mm:ss）。 */
    ISO_LOCAL_DATE_TIME,
    /** ISO-8601 UTC 时间点（Instant）。 */
    INSTANT,
    /** Unix 毫秒时间戳。 */
    EPOCH_MILLIS,
    /** Unix 秒时间戳。 */
    EPOCH_SECONDS
}
