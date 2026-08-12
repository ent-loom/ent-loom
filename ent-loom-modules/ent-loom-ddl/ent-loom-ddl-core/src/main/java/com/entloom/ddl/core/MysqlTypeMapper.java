package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlFieldMetadata;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Java 类型到 MySQL 类型的基础映射。
 */
public final class MysqlTypeMapper {
    public String toSqlType(DdlFieldMetadata field) {
        Class<?> type = field.javaType();

        if (type == String.class) {
            int length = field.length() > 0 ? field.length() : 200;
            return "varchar(" + length + ")";
        }
        if (type == Long.class || type == Long.TYPE) {
            return "bigint";
        }
        if (type == Integer.class || type == Integer.TYPE) {
            return "int";
        }
        if (type == Boolean.class || type == Boolean.TYPE) {
            return "tinyint(1)";
        }
        if (type == BigDecimal.class || type == Double.class || type == Double.TYPE
                || type == Float.class || type == Float.TYPE) {
            int precision = field.precision() > 0 ? field.precision() : (field.length() > 0 ? field.length() : 20);
            int scale = field.scale() >= 0 ? field.scale() : 6;
            return "decimal(" + precision + "," + scale + ")";
        }
        if (type == LocalDateTime.class || "java.sql.Timestamp".equals(type.getName())) {
            return "datetime";
        }
        if (type == LocalDate.class || "java.sql.Date".equals(type.getName())) {
            return "date";
        }
        if (type == LocalTime.class || "java.sql.Time".equals(type.getName())) {
            return "time";
        }
        if (type == byte[].class) {
            return "blob";
        }
        if (Enum.class.isAssignableFrom(type)) {
            return "varchar(64)";
        }
        return "varchar(255)";
    }
}
