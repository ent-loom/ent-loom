package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.LocalTime;

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
        if (normalized instanceof Instant) {
            return java.sql.Timestamp.from((Instant) normalized);
        }
        if (normalized instanceof LocalTime) {
            if (((LocalTime) normalized).getNano() != 0) {
                throw new ValidationException("LocalTime 列当前仅支持秒精度，不能包含小数秒: " + field.getFieldName());
            }
            return java.sql.Time.valueOf((LocalTime) normalized);
        }
        if (normalized instanceof Character) {
            return normalized.toString();
        }
        return normalized;
    }
}
