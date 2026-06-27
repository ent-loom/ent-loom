package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 统计操作类型。
 */
public enum StatsOperation implements CrudScopedOperation {
    /** 执行统计查询。 */
    QUERY,
    /** 统计预览。 */
    PREVIEW;

    @Override
    public CrudOperationDomain domain() {
        return CrudOperationDomain.STATS;
    }

    public static StatsOperation from(String raw) {
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
