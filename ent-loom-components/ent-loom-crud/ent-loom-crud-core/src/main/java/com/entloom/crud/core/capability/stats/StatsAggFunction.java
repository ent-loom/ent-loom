package com.entloom.crud.core.capability.stats;

import java.util.Locale;

/**
 * 聚合函数。
 */
public enum StatsAggFunction {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX;

    public static StatsAggFunction from(String raw) {
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
