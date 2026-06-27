package com.entloom.crud.core.capability.stats;

import java.util.Locale;

/**
 * 时间分桶粒度。
 */
public enum StatsTimeGranularity {
    DAY;

    public static StatsTimeGranularity from(String raw) {
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
