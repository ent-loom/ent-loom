package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 分页总数策略。
 */
public enum PageCountMode {
    /** 返回精确 total/totalPages。 */
    EXACT,
    /** 不查询总数，改为返回 hasNext。 */
    NONE
    ;

    public static PageCountMode from(String raw) {
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
