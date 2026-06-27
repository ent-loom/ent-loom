package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 排序目标类型。
 */
public enum SortTarget {
    /** 自动推断（仅用于入参层）。 */
    AUTO,
    /** 实体字段排序。 */
    FIELD,
    /** 统计指标排序。 */
    METRIC,
    /** 统计维度排序。 */
    DIMENSION
    ;

    public static SortTarget from(String raw) {
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
