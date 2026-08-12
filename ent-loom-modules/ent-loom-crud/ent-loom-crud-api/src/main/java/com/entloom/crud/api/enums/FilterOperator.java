package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 过滤操作符。
 */
public enum FilterOperator {
    /** 等于。 */
    EQ,
    /** 不等于。 */
    NE,
    /** 大于。 */
    GT,
    /** 大于等于。 */
    GE,
    /** 小于。 */
    LT,
    /** 小于等于。 */
    LE,
    /** 包含。 */
    IN,
    /** 不包含。 */
    NOT_IN,
    /** 区间。 */
    BETWEEN,
    /** 模糊匹配。 */
    LIKE,
    /** 判空。 */
    IS_NULL,
    /** 判非空。 */
    IS_NOT_NULL
    ;

    public static FilterOperator from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
