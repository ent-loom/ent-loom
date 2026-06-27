package com.entloom.crud.starter.web.time;

import java.util.Locale;

/**
 * 框架内置时间预设。
 */
public enum CrudBuiltinTimePreset {
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    LAST_MONTH;

    public static CrudBuiltinTimePreset from(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return CrudBuiltinTimePreset.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
