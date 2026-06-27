package com.entloom.crud.core.capability.exporting;

import com.entloom.crud.api.model.ExportDisplayEnum;
import com.entloom.crud.core.exception.ValidationException;
import com.fasterxml.jackson.annotation.JsonValue;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 默认导出展示值渲染器。
 */
public class DefaultExportValueRenderer implements ExportValueRenderer {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Object NOT_FORMATTED = new Object();

    private final ExportDictionaryResolver dictionaryResolver;

    public DefaultExportValueRenderer() {
        this(null);
    }

    public DefaultExportValueRenderer(ExportDictionaryResolver dictionaryResolver) {
        this.dictionaryResolver = dictionaryResolver;
    }

    @Override
    public Map<String, Object> renderRow(List<ExportColumn> columns, Map<String, Object> rawRow, ExportRenderOptions options) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        if (columns == null) {
            return row;
        }
        for (ExportColumn column : columns) {
            Object raw = rawRow == null ? null : rawRow.get(column.getSourceField());
            row.put(column.getKey(), renderValue(new ExportFieldContext(column, options), raw));
        }
        return row;
    }

    private Object renderValue(ExportFieldContext context, Object value) {
        if (value == null) {
            return "";
        }
        Class<?> declaredType = wrap(context.getDeclaredType());
        Object dictionaryValue = resolveDictionaryValue(context, value);
        if (dictionaryValue != NOT_FORMATTED) {
            return dictionaryValue;
        }
        Object formatted = formatByHint(context, declaredType, value);
        if (formatted != NOT_FORMATTED) {
            return formatted;
        }
        Object enumValue = resolveEnumValue(declaredType, value);
        if (enumValue instanceof Enum<?>) {
            return enumText((Enum<?>) enumValue);
        }
        Object booleanValue = resolveBooleanValue(declaredType, value);
        if (booleanValue instanceof Boolean) {
            return ((Boolean) booleanValue).booleanValue() ? "是" : "否";
        }
        if (isTemporalType(declaredType) || isTemporalValue(value)) {
            return formatTemporal(value, context.getRenderOptions(), null);
        }
        if (value instanceof BigDecimal || value instanceof BigInteger || value instanceof Number) {
            return value;
        }
        return String.valueOf(value);
    }

    private Object resolveDictionaryValue(ExportFieldContext context, Object value) {
        if (dictionaryResolver == null || context.getFieldMeta() == null || context.getFieldMeta().getDictionaryCode() == null) {
            return NOT_FORMATTED;
        }
        String text = dictionaryResolver.resolve(context, value);
        return text == null ? NOT_FORMATTED : text;
    }

    private Object formatByHint(ExportFieldContext context, Class<?> declaredType, Object value) {
        String format = context.getColumn() == null ? null : context.getColumn().getFormat();
        if (format == null || format.trim().isEmpty()) {
            return NOT_FORMATTED;
        }
        String trimmed = format.trim();
        if (isTextFormat(trimmed)) {
            return displayText(value);
        }
        Object booleanValue = resolveBooleanValue(declaredType, value);
        if (booleanValue instanceof Boolean) {
            String booleanText = formatBoolean((Boolean) booleanValue, trimmed);
            return booleanText == null ? NOT_FORMATTED : booleanText;
        }
        if (isTemporalType(declaredType) || isTemporalValue(value)) {
            return formatTemporal(value, context.getRenderOptions(), resolveTemporalFormatter(trimmed));
        }
        if (value instanceof Number) {
            return formatNumber((Number) value, trimmed);
        }
        return NOT_FORMATTED;
    }

    private boolean isTextFormat(String format) {
        String normalized = format.toLowerCase(Locale.ROOT);
        return "text".equals(normalized) || "string".equals(normalized) || "plain".equals(normalized);
    }

    private String displayText(Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof BigInteger) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private String formatBoolean(Boolean value, String format) {
        String mappingText = format.trim();
        if (mappingText.indexOf('=') >= 0) {
            String[] entries = mappingText.split("[;,]");
            for (String entry : entries) {
                String[] parts = entry.split("=", 2);
                if (parts.length == 2 && booleanKeyMatches(value.booleanValue(), parts[0].trim())) {
                    return parts[1].trim();
                }
            }
            return null;
        }
        String[] parts = mappingText.split("/", 2);
        if (parts.length == 2) {
            return value.booleanValue() ? parts[0].trim() : parts[1].trim();
        }
        return null;
    }

    private boolean booleanKeyMatches(boolean value, String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (value) {
            return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
        }
        return "false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized);
    }

    private String formatNumber(Number value, String format) {
        try {
            DecimalFormat decimalFormat = new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.ROOT));
            return decimalFormat.format(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("导出数字格式无效: " + format);
        }
    }

    private DateTimeFormatter resolveTemporalFormatter(String format) {
        String normalized = format.toLowerCase(Locale.ROOT);
        if ("date".equals(normalized)) {
            return DATE_FORMATTER;
        }
        if ("datetime".equals(normalized) || "date-time".equals(normalized)) {
            return DATE_TIME_FORMATTER;
        }
        if ("time".equals(normalized)) {
            return TIME_FORMATTER;
        }
        try {
            return DateTimeFormatter.ofPattern(format);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("导出时间格式无效: " + format);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveEnumValue(Class<?> declaredType, Object value) {
        if (value instanceof Enum<?>) {
            return value;
        }
        if (declaredType != null && declaredType.isEnum() && value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return value;
            }
            try {
                return Enum.valueOf((Class<? extends Enum>) declaredType.asSubclass(Enum.class), text);
            } catch (IllegalArgumentException ex) {
                return value;
            }
        }
        return value;
    }

    private String enumText(Enum<?> value) {
        if (value instanceof ExportDisplayEnum) {
            String desc = ((ExportDisplayEnum) value).getDesc();
            if (desc != null && !desc.trim().isEmpty()) {
                return desc;
            }
        }
        String text = readNoArgText(value, "getDesc");
        if (text != null) {
            return text;
        }
        text = readNoArgText(value, "getDescription");
        if (text != null) {
            return text;
        }
        text = readNoArgText(value, "getLabel");
        if (text != null) {
            return text;
        }
        text = readJsonValueText(value);
        return text == null ? value.name() : text;
    }

    private String readNoArgText(Object value, String methodName) {
        try {
            Method method;
            try {
                method = value.getClass().getMethod(methodName);
            } catch (NoSuchMethodException ex) {
                method = value.getClass().getDeclaredMethod(methodName);
            }
            if (method.getParameterTypes().length != 0) {
                return null;
            }
            method.setAccessible(true);
            Object result = method.invoke(value);
            return result == null ? null : String.valueOf(result);
        } catch (Exception ex) {
            return null;
        }
    }

    private String readJsonValueText(Object value) {
        Method[] methods = value.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getParameterTypes().length == 0 && method.getAnnotation(JsonValue.class) != null) {
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    return result == null ? null : String.valueOf(result);
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        methods = value.getClass().getMethods();
        for (Method method : methods) {
            if (method.getParameterTypes().length == 0 && method.getAnnotation(JsonValue.class) != null) {
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    return result == null ? null : String.valueOf(result);
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private Object resolveBooleanValue(Class<?> declaredType, Object value) {
        boolean declaredBoolean = Boolean.class.equals(declaredType);
        if (value instanceof Boolean) {
            return value;
        }
        if (!declaredBoolean) {
            return value;
        }
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            if (number == 0) {
                return Boolean.FALSE;
            }
            if (number == 1) {
                return Boolean.TRUE;
            }
            return value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(text) || "1".equals(text)) {
                return Boolean.TRUE;
            }
            if ("false".equals(text) || "0".equals(text)) {
                return Boolean.FALSE;
            }
        }
        return value;
    }

    private Object formatTemporal(Object value, ExportRenderOptions options, DateTimeFormatter formatter) {
        DateTimeFormatter effectiveFormatter = formatter == null ? DATE_TIME_FORMATTER : formatter;
        if (value instanceof LocalDateTime) {
            return formatTemporalValue(effectiveFormatter, value);
        }
        if (value instanceof LocalDate) {
            return formatTemporalValue(formatter == null ? DATE_FORMATTER : formatter, value);
        }
        if (value instanceof LocalTime) {
            return formatTemporalValue(formatter == null ? TIME_FORMATTER : formatter, value);
        }
        ZoneId zoneId = ZoneId.of(options == null || options.getTimezone() == null ? "UTC" : options.getTimezone());
        if (value instanceof Instant) {
            return formatTemporalValue(effectiveFormatter, ((Instant) value).atZone(zoneId));
        }
        if (value instanceof OffsetDateTime) {
            return formatTemporalValue(effectiveFormatter, ((OffsetDateTime) value).atZoneSameInstant(zoneId));
        }
        if (value instanceof ZonedDateTime) {
            return formatTemporalValue(effectiveFormatter, ((ZonedDateTime) value).withZoneSameInstant(zoneId));
        }
        Object sqlTemporal = formatSqlTemporal(value, zoneId, formatter);
        if (sqlTemporal != null) {
            return sqlTemporal;
        }
        if (value instanceof Date) {
            return formatTemporalValue(effectiveFormatter, ((Date) value).toInstant().atZone(zoneId));
        }
        return String.valueOf(value);
    }

    private Object formatTemporalValue(DateTimeFormatter formatter, Object temporal) {
        try {
            if (temporal instanceof LocalDateTime) {
                return formatter.format((LocalDateTime) temporal);
            }
            if (temporal instanceof LocalDate) {
                return formatter.format((LocalDate) temporal);
            }
            if (temporal instanceof LocalTime) {
                return formatter.format((LocalTime) temporal);
            }
            if (temporal instanceof ZonedDateTime) {
                return formatter.format((ZonedDateTime) temporal);
            }
            return String.valueOf(temporal);
        } catch (RuntimeException ex) {
            throw new ValidationException("导出时间格式与字段类型不匹配");
        }
    }

    private Object formatSqlTemporal(Object value, ZoneId zoneId, DateTimeFormatter formatter) {
        String className = value.getClass().getName();
        if ("java.sql.Timestamp".equals(className)) {
            try {
                Method method = value.getClass().getMethod("toInstant");
                Object instant = method.invoke(value);
                if (instant instanceof Instant) {
                    return formatTemporalValue(formatter == null ? DATE_TIME_FORMATTER : formatter, ((Instant) instant).atZone(zoneId));
                }
            } catch (ValidationException ex) {
                throw ex;
            } catch (Exception ex) {
                return String.valueOf(value);
            }
        }
        if ("java.sql.Date".equals(className)) {
            try {
                Method method = value.getClass().getMethod("toLocalDate");
                Object localDate = method.invoke(value);
                if (localDate instanceof LocalDate) {
                    return formatTemporalValue(formatter == null ? DATE_FORMATTER : formatter, localDate);
                }
            } catch (ValidationException ex) {
                throw ex;
            } catch (Exception ex) {
                return String.valueOf(value);
            }
        }
        if ("java.sql.Time".equals(className)) {
            try {
                Method method = value.getClass().getMethod("toLocalTime");
                Object localTime = method.invoke(value);
                if (localTime instanceof LocalTime) {
                    return formatTemporalValue(formatter == null ? TIME_FORMATTER : formatter, localTime);
                }
            } catch (ValidationException ex) {
                throw ex;
            } catch (Exception ex) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean isTemporalType(Class<?> type) {
        return type != null
            && (LocalDate.class.isAssignableFrom(type)
            || LocalDateTime.class.isAssignableFrom(type)
            || LocalTime.class.isAssignableFrom(type)
            || Instant.class.isAssignableFrom(type)
            || Date.class.isAssignableFrom(type)
            || OffsetDateTime.class.isAssignableFrom(type)
            || ZonedDateTime.class.isAssignableFrom(type));
    }

    private boolean isTemporalValue(Object value) {
        return value instanceof LocalDate
            || value instanceof LocalDateTime
            || value instanceof LocalTime
            || value instanceof Instant
            || value instanceof Date
            || value instanceof OffsetDateTime
            || value instanceof ZonedDateTime;
    }

    private Class<?> wrap(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (boolean.class.equals(type)) {
            return Boolean.class;
        }
        if (int.class.equals(type)) {
            return Integer.class;
        }
        if (long.class.equals(type)) {
            return Long.class;
        }
        if (double.class.equals(type)) {
            return Double.class;
        }
        if (float.class.equals(type)) {
            return Float.class;
        }
        if (short.class.equals(type)) {
            return Short.class;
        }
        if (byte.class.equals(type)) {
            return Byte.class;
        }
        if (char.class.equals(type)) {
            return Character.class;
        }
        return type;
    }
}
