package com.entloom.base.util.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 通用字符串值的序列化/反序列化工具。
 */
public final class TypedValueCodec {
    private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private TypedValueCodec() {
    }

    /**
     * 将字符串按指定类型反序列化为 Java 对象。
     */
    public static Object deserialize(String rawValue, TypedValueType type) {
        if (rawValue == null) {
            return null;
        }
        TypedValueType resolvedType = normalize(type);
        String trimmed = rawValue.trim();
        switch (resolvedType) {
            case UNSET:
            case STRING:
                return rawValue;
            case BOOLEAN:
                return parseBoolean(trimmed);
            case INT:
                return Integer.valueOf(trimmed);
            case LONG:
                return Long.valueOf(trimmed);
            case DECIMAL:
                return new BigDecimal(trimmed);
            case JSON:
                validateJsonText(trimmed);
                return trimmed;
            case ISO_LOCAL_DATE:
                return LocalDate.parse(trimmed, ISO_LOCAL_DATE);
            case ISO_LOCAL_DATE_TIME:
                return LocalDateTime.parse(trimmed, ISO_LOCAL_DATE_TIME);
            case INSTANT:
                return Instant.parse(trimmed);
            case EPOCH_MILLIS:
            case EPOCH_SECONDS:
                return Long.valueOf(trimmed);
            default:
                throw new IllegalArgumentException("Unsupported value type: " + resolvedType);
        }
    }

    /**
     * 将 Java 对象按指定类型序列化为字符串。
     */
    public static String serialize(Object value, TypedValueType type) {
        if (value == null) {
            return "";
        }
        TypedValueType resolvedType = normalize(type);
        switch (resolvedType) {
            case UNSET:
            case STRING:
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(toBoolean(value));
            case INT:
                return String.valueOf(toInt(value));
            case LONG:
                return String.valueOf(toLong(value));
            case DECIMAL:
                return toDecimal(value).toPlainString();
            case JSON:
                String jsonText = String.valueOf(value).trim();
                validateJsonText(jsonText);
                return jsonText;
            case ISO_LOCAL_DATE:
                return toLocalDate(value).format(ISO_LOCAL_DATE);
            case ISO_LOCAL_DATE_TIME:
                return toLocalDateTime(value).format(ISO_LOCAL_DATE_TIME);
            case INSTANT:
                return toInstant(value).toString();
            case EPOCH_MILLIS:
                if (value instanceof Instant) {
                    return String.valueOf(((Instant) value).toEpochMilli());
                }
                return String.valueOf(toLong(value));
            case EPOCH_SECONDS:
                if (value instanceof Instant) {
                    return String.valueOf(((Instant) value).getEpochSecond());
                }
                return String.valueOf(toLong(value));
            default:
                throw new IllegalArgumentException("Unsupported value type: " + resolvedType);
        }
    }

    /**
     * 根据 Java 类型推断值类型。
     */
    public static TypedValueType inferType(Class<?> javaType) {
        if (javaType == null) {
            return TypedValueType.UNSET;
        }
        if (String.class.equals(javaType) || CharSequence.class.isAssignableFrom(javaType)) {
            return TypedValueType.STRING;
        }
        if (Boolean.class.equals(javaType) || boolean.class.equals(javaType)) {
            return TypedValueType.BOOLEAN;
        }
        if (Integer.class.equals(javaType) || int.class.equals(javaType)
            || Short.class.equals(javaType) || short.class.equals(javaType)
            || Byte.class.equals(javaType) || byte.class.equals(javaType)) {
            return TypedValueType.INT;
        }
        if (Long.class.equals(javaType) || long.class.equals(javaType)) {
            return TypedValueType.LONG;
        }
        if (BigDecimal.class.equals(javaType) || BigInteger.class.equals(javaType)
            || Double.class.equals(javaType) || double.class.equals(javaType)
            || Float.class.equals(javaType) || float.class.equals(javaType)) {
            return TypedValueType.DECIMAL;
        }
        if (LocalDate.class.equals(javaType)) {
            return TypedValueType.ISO_LOCAL_DATE;
        }
        if (LocalDateTime.class.equals(javaType)) {
            return TypedValueType.ISO_LOCAL_DATE_TIME;
        }
        if (Instant.class.equals(javaType)) {
            return TypedValueType.INSTANT;
        }
        return TypedValueType.UNSET;
    }

    private static TypedValueType normalize(TypedValueType type) {
        return type == null ? TypedValueType.UNSET : type;
    }

    private static boolean parseBoolean(String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value: " + raw);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return parseBoolean(String.valueOf(value).trim());
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        return LocalDate.parse(String.valueOf(value).trim(), ISO_LOCAL_DATE);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return LocalDateTime.parse(String.valueOf(value).trim(), ISO_LOCAL_DATE_TIME);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant) {
            return (Instant) value;
        }
        return Instant.parse(String.valueOf(value).trim());
    }

    private static void validateJsonText(String jsonText) {
        if (jsonText.isEmpty()) {
            throw new IllegalArgumentException("JSON value must not be empty");
        }
        boolean object = jsonText.startsWith("{") && jsonText.endsWith("}");
        boolean array = jsonText.startsWith("[") && jsonText.endsWith("]");
        if (!object && !array) {
            throw new IllegalArgumentException("Invalid JSON text: " + jsonText);
        }
    }
}
