package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * CRUD 查询响应空字段模式。
 */
public enum CrudNullFieldMode {
    /** 保留值为 null 的字段。 */
    INCLUDE("保留空字段"),
    /** 隐藏值为 null 的字段。 */
    OMIT("隐藏空字段")
    ;

    private final String displayName;

    CrudNullFieldMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CrudNullFieldMode from(String raw) {
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
