package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 读请求返回模式。
 */
public enum CrudReadResultMode {
    /** 返回实体对象。 */
    ENTITY,
    /** 返回通用记录对象。 */
    MAP
    ;

    public static CrudReadResultMode from(String raw) {
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
