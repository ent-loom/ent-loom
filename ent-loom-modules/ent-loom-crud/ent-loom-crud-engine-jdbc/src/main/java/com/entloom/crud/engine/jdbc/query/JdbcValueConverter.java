package com.entloom.crud.engine.jdbc.query;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * JDBC 查询结果值类型适配器。
 */
class JdbcValueConverter {

    /**
     * 适配 JDBC 原始值到目标字段类型。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    Object adapt(Class<?> targetType, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        Class<?> boxedType = boxType(targetType);
        if (boxedType.isInstance(rawValue)) {
            return rawValue;
        }

        if (boxedType.isEnum()) {
            if (rawValue instanceof String) {
                String name = ((String) rawValue).trim();
                if (name.isEmpty()) {
                    return null;
                }
                return Enum.valueOf((Class<Enum>) boxedType, name);
            }
            if (rawValue instanceof Number) {
                int ordinal = ((Number) rawValue).intValue();
                Object[] constants = boxedType.getEnumConstants();
                return (ordinal >= 0 && ordinal < constants.length) ? constants[ordinal] : null;
            }
        }

        if (boxedType == String.class) {
            return String.valueOf(rawValue);
        }
        if (boxedType == Integer.class) {
            return rawValue instanceof Number ? ((Number) rawValue).intValue() : Integer.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == Long.class) {
            return rawValue instanceof Number ? ((Number) rawValue).longValue() : Long.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == Double.class) {
            return rawValue instanceof Number ? ((Number) rawValue).doubleValue() : Double.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == Float.class) {
            return rawValue instanceof Number ? ((Number) rawValue).floatValue() : Float.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == Short.class) {
            return rawValue instanceof Number ? ((Number) rawValue).shortValue() : Short.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == Byte.class) {
            return rawValue instanceof Number ? ((Number) rawValue).byteValue() : Byte.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == BigDecimal.class) {
            if (rawValue instanceof BigDecimal) {
                return rawValue;
            }
            if (rawValue instanceof Number) {
                return BigDecimal.valueOf(((Number) rawValue).doubleValue());
            }
            return new BigDecimal(String.valueOf(rawValue));
        }
        if (boxedType == Boolean.class) {
            if (rawValue instanceof Number) {
                return ((Number) rawValue).intValue() != 0;
            }
            if (rawValue instanceof String) {
                String value = ((String) rawValue).trim();
                if ("1".equals(value)) {
                    return true;
                }
                if ("0".equals(value)) {
                    return false;
                }
            }
            return Boolean.valueOf(String.valueOf(rawValue));
        }
        if (boxedType == LocalDate.class) {
            if (rawValue instanceof java.sql.Date) {
                return ((java.sql.Date) rawValue).toLocalDate();
            }
            if (rawValue instanceof Timestamp) {
                return ((Timestamp) rawValue).toLocalDateTime().toLocalDate();
            }
            if (rawValue instanceof Date) {
                return new java.sql.Date(((Date) rawValue).getTime()).toLocalDate();
            }
        }
        if (boxedType == LocalDateTime.class) {
            if (rawValue instanceof Timestamp) {
                return ((Timestamp) rawValue).toLocalDateTime();
            }
            if (rawValue instanceof java.sql.Date) {
                return ((java.sql.Date) rawValue).toLocalDate().atStartOfDay();
            }
            if (rawValue instanceof Date) {
                return new Timestamp(((Date) rawValue).getTime()).toLocalDateTime();
            }
        }
        return rawValue;
    }

    /**
     * 将基础类型转换为装箱类型。
     */
    private Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
