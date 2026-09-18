package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 将 DAO 字段值规范化为稳定的 JDBC 绑定值。
 *
 * <p>枚举绑定其 name；LocalDate 和 LocalDateTime 不携带时区，分别绑定为 SQL DATE 和 TIMESTAMP，避免依赖
 * JDBC 驱动对 Java 8 时间类型的隐式处理。</p>
 */
final class JdbcEntityValueBinder {
    private JdbcEntityValueBinder() {
    }

    static Object normalize(EntityFieldMeta field, Object value) {
        Object normalized = RowConstraintNormalizer.normalizeValue(field, value);
        if (normalized instanceof Enum<?>) {
            return ((Enum<?>) normalized).name();
        }
        if (normalized instanceof LocalDate) {
            return java.sql.Date.valueOf((LocalDate) normalized);
        }
        if (normalized instanceof LocalDateTime) {
            return java.sql.Timestamp.valueOf((LocalDateTime) normalized);
        }
        return normalized;
    }
}
