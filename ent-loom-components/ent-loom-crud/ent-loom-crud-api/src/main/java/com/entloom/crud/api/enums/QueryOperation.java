package com.entloom.crud.api.enums;

import java.util.Locale;

/**
 * 查询操作类型。
 */
public enum QueryOperation implements CrudScopedOperation {
    /** 分页查询。 */
    PAGE,
    /** 列表查询（受 limit 约束）。 */
    LIST,
    /** 可空唯一查询：允许不存在，0 条返回 null，多条为 QUERY_NOT_UNIQUE。 */
    FIND_ONE,
    /** 资源详情查询：必须存在，0 条为 NotFound，多条为 QUERY_NOT_UNIQUE。 */
    DETAIL;

    @Override
    public CrudOperationDomain domain() {
        return CrudOperationDomain.QUERY;
    }

    public static QueryOperation from(String raw) {
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
