package com.entloom.crud.starter.web.time;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.spring.config.CrudProperties;
import com.entloom.crud.starter.web.dto.CrudTimeFilter;
import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.time.temporal.TemporalAccessor;

/**
 * time 过滤参数解析器。
 */
public class CrudTimeFilterResolver {
    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter DATE_TIME_SPACE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId defaultZoneId;
    private final String defaultTimeField;
    private final List<CrudTimePresetResolver> presetResolvers;

    public CrudTimeFilterResolver(String defaultTimezone) {
        this(
            defaultTimezone,
            null,
            Collections.<CrudTimePresetResolver>singletonList(new BuiltinCrudTimePresetResolver())
        );
    }

    public CrudTimeFilterResolver(String defaultTimezone, List<CrudTimePresetResolver> presetResolvers) {
        this(defaultTimezone, null, presetResolvers);
    }

    public CrudTimeFilterResolver(
        String defaultTimezone,
        String defaultTimeField,
        List<CrudTimePresetResolver> presetResolvers
    ) {
        this.defaultZoneId = resolveDefaultZone(defaultTimezone);
        this.defaultTimeField = normalizeText(defaultTimeField);
        if (presetResolvers == null || presetResolvers.isEmpty()) {
            this.presetResolvers = Collections.<CrudTimePresetResolver>emptyList();
            return;
        }
        List<CrudTimePresetResolver> copied = new ArrayList<CrudTimePresetResolver>(presetResolvers);
        Collections.sort(copied, new Comparator<CrudTimePresetResolver>() {
            @Override
            public int compare(CrudTimePresetResolver left, CrudTimePresetResolver right) {
                int orderCompare = Integer.compare(left.order(), right.order());
                if (orderCompare != 0) {
                    return orderCompare;
                }
                return left.getClass().getName().compareTo(right.getClass().getName());
            }
        });
        this.presetResolvers = Collections.unmodifiableList(copied);
    }

    /**
     * 解析时间快捷过滤参数并归一化为标准 filters。
     */
    public List<QueryFilter> resolveFilters(CrudTimeFilter time, Class<?> rootType) {
        if (time == null) {
            return Collections.emptyList();
        }
        String startRaw = normalizeText(time.getStart());
        String endRaw = normalizeText(time.getEnd());
        String preset = normalizeText(time.getPreset());
        if (startRaw == null && endRaw == null && preset == null) {
            return Collections.emptyList();
        }

        String field = resolveTimeField(time.getField());
        if (field == null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "time.field 不能为空");
        }

        ZoneId zoneId = resolveZoneId(time.getTimezone());
        ZonedDateTime start;
        ZonedDateTime end;
        if (startRaw != null || endRaw != null) {
            start = parseStartDateTimeIfPresent(startRaw, zoneId);
            end = parseEndDateTimeIfPresent(endRaw, zoneId);
        } else {
            CrudTimeRange range = resolvePresetRange(preset, zoneId);
            start = range.getStart();
            end = range.getEnd();
        }
        if (start != null && end != null && !start.isBefore(end)) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "time.start 必须早于 time.end");
        }
        List<QueryFilter> filters = new ArrayList<QueryFilter>(2);
        if (start != null) {
            Object startValue = resolveBoundaryValue(rootType, field, start);
            filters.add(new QueryFilter(field, FilterOperator.GE, startValue));
        }
        if (end != null) {
            Object endValue = resolveBoundaryValue(rootType, field, end);
            filters.add(new QueryFilter(field, FilterOperator.LT, endValue));
        }
        return filters;
    }

    private ZonedDateTime parseStartDateTimeIfPresent(String raw, ZoneId zoneId) {
        return raw == null ? null : parseDateTime(raw, zoneId, false);
    }

    private ZonedDateTime parseEndDateTimeIfPresent(String raw, ZoneId zoneId) {
        return raw == null ? null : parseDateTime(raw, zoneId, true);
    }

    private CrudTimeRange resolvePresetRange(String preset, ZoneId zoneId) {
        for (CrudTimePresetResolver resolver : presetResolvers) {
            CrudTimeRange resolved = resolver.resolve(preset, zoneId);
            if (resolved != null) {
                return resolved;
            }
        }
        throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "不支持的时间预设: " + preset);
    }

    private ZoneId resolveZoneId(String raw) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return defaultZoneId;
        }
        try {
            return ZoneId.of(normalized);
        } catch (Exception ex) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "不支持的时区: " + raw);
        }
    }

    private ZonedDateTime parseDateTime(String raw, ZoneId zoneId, boolean endBoundary) {
        if (raw == null) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "时间不能为空");
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "时间不能为空");
        }
        try {
            TemporalAccessor accessor = ISO_DATE_TIME_FORMATTER.parseBest(
                value,
                ZonedDateTime::from,
                OffsetDateTime::from,
                LocalDateTime::from,
                Instant::from
            );
            if (accessor instanceof ZonedDateTime) {
                return ((ZonedDateTime) accessor).withZoneSameInstant(zoneId);
            }
            if (accessor instanceof OffsetDateTime) {
                return ((OffsetDateTime) accessor).atZoneSameInstant(zoneId);
            }
            if (accessor instanceof LocalDateTime) {
                return ((LocalDateTime) accessor).atZone(zoneId);
            }
            return ((Instant) accessor).atZone(zoneId);
        } catch (DateTimeParseException ignore) {
            // ignore
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_SPACE_FORMATTER).atZone(zoneId);
        } catch (DateTimeParseException ignore) {
            // ignore
        }
        try {
            ZonedDateTime boundary = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zoneId);
            return endBoundary ? boundary.plusDays(1) : boundary;
        } catch (DateTimeParseException ignore) {
            // ignore
        }
        throw new CrudException(
            CrudErrorCode.VALIDATION_ERROR,
            "不支持的时间格式: " + raw + "，仅支持 ISO-8601、yyyy-MM-dd HH:mm:ss、yyyy-MM-dd"
        );
    }

    private Object resolveBoundaryValue(Class<?> rootType, String field, ZonedDateTime boundary) {
        Class<?> fieldType = resolveFieldType(rootType, field);
        if (fieldType == null) {
            if (!isNestedField(field)) {
                throw new CrudException(CrudErrorCode.VALIDATION_ERROR, "time.field 不存在: " + field);
            }
            return boundary.toLocalDateTime();
        }
        if (LocalDate.class.isAssignableFrom(fieldType)) {
            return boundary.toLocalDate();
        }
        if (Date.class.isAssignableFrom(fieldType)) {
            return Date.valueOf(boundary.toLocalDate());
        }
        if (LocalDateTime.class.isAssignableFrom(fieldType)) {
            return boundary.toLocalDateTime();
        }
        if (Timestamp.class.isAssignableFrom(fieldType)) {
            return Timestamp.valueOf(boundary.toLocalDateTime());
        }
        if (Instant.class.isAssignableFrom(fieldType)) {
            return boundary.toInstant();
        }
        if (ZonedDateTime.class.isAssignableFrom(fieldType)) {
            return boundary;
        }
        if (java.time.OffsetDateTime.class.isAssignableFrom(fieldType)) {
            return boundary.toOffsetDateTime();
        }
        if (java.util.Date.class.isAssignableFrom(fieldType)) {
            return java.util.Date.from(boundary.toInstant());
        }
        return boundary.toLocalDateTime();
    }

    private Class<?> resolveFieldType(Class<?> rootType, String field) {
        if (isNestedField(field)) {
            return null;
        }
        Class<?> cursor = rootType;
        while (cursor != null && cursor != Object.class) {
            try {
                Field declaredField = cursor.getDeclaredField(field);
                return declaredField.getType();
            } catch (NoSuchFieldException ex) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private boolean isNestedField(String field) {
        return field != null && field.contains(".");
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ZoneId resolveDefaultZone(String raw) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return ZoneId.of(CrudProperties.Controller.DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(normalized);
        } catch (Exception ignore) {
            return ZoneId.of(CrudProperties.Controller.DEFAULT_TIMEZONE);
        }
    }

    private String resolveTimeField(String rawField) {
        String field = normalizeText(rawField);
        return field == null ? defaultTimeField : field;
    }

}
