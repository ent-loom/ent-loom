package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 排序方向。
 */
public enum SortDirection {
    /** 升序。 */
    ASC,
    /** 降序。 */
    DESC
    ;

    public static SortDirection from(String raw) {
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
