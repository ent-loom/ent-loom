package com.entloom.crud.api.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 通用 CRUD 记录模型。
 */
public class CrudRecord {
    private final Map<String, Object> values;

    public CrudRecord() {
        this(null);
    }

    public CrudRecord(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), normalizeValue(entry.getValue()));
            }
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    public static CrudRecord copyOf(Map<String, Object> source) {
        return new CrudRecord(source);
    }

    public static CrudRecord copyOf(Object source) {
        if (source instanceof CrudRecord) {
            return (CrudRecord) source;
        }
        if (source instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) source;
            return new CrudRecord(map);
        }
        return null;
    }

    public Object get(String key) {
        return raw(key);
    }

    public Object raw(String key) {
        return values.get(key);
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public Set<String> keys() {
        return values.keySet();
    }

    @JsonValue
    public Map<String, Object> asMap() {
        return values;
    }

    public String string(String key) {
        return get(key, String.class);
    }

    public String requiredString(String key) {
        return required(key, String.class);
    }

    public Long longValue(String key) {
        return get(key, Long.class);
    }

    public Long requiredLong(String key) {
        return required(key, Long.class);
    }

    public Integer intValue(String key) {
        return get(key, Integer.class);
    }

    public Integer requiredInt(String key) {
        return required(key, Integer.class);
    }

    public Boolean booleanValue(String key) {
        return get(key, Boolean.class);
    }

    public Boolean requiredBoolean(String key) {
        return required(key, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    public CrudRecord record(String key) {
        Object value = raw(key);
        if (value instanceof CrudRecord) {
            return (CrudRecord) value;
        }
        return value instanceof Map<?, ?> ? copyOf((Map<String, Object>) value) : null;
    }

    public CrudRecord requiredRecord(String key) {
        CrudRecord record = record(key);
        if (record == null) {
            throw new IllegalStateException("字段 " + key + " 不能为空");
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    public List<CrudRecord> records(String key) {
        Object value = raw(key);
        List<CrudRecord> result = new ArrayList<CrudRecord>();
        if (!(value instanceof List<?>)) {
            return result;
        }
        for (Object item : (List<?>) value) {
            if (item instanceof CrudRecord) {
                result.add((CrudRecord) item);
            } else if (item instanceof Map<?, ?>) {
                result.add(copyOf((Map<String, Object>) item));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public <T> T get(String key, Class<T> targetType) {
        return convert(raw(key), targetType);
    }

    public <T> T required(String key, Class<T> targetType) {
        T value = get(key, targetType);
        if (value == null) {
            throw new IllegalStateException("字段 " + key + " 不能为空");
        }
        return value;
    }

    @Override
    public String toString() {
        return values.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CrudRecord)) {
            return false;
        }
        CrudRecord that = (CrudRecord) other;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof CrudRecord) {
            return value;
        }
        if (value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return new CrudRecord(map);
        }
        if (value instanceof List<?>) {
            List<Object> items = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                items.add(normalizeValue(item));
            }
            return Collections.unmodifiableList(items);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> targetType) {
        if (value == null || targetType == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        if (targetType == String.class) {
            return (T) String.valueOf(value);
        }
        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return (T) Long.valueOf(((Number) value).longValue());
            }
            return (T) Long.valueOf(String.valueOf(value));
        }
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return (T) Integer.valueOf(((Number) value).intValue());
            }
            return (T) Integer.valueOf(String.valueOf(value));
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return (T) value;
            }
            if (value instanceof Number) {
                return (T) Boolean.valueOf(((Number) value).intValue() != 0);
            }
            String normalized = String.valueOf(value).trim();
            if ("1".equals(normalized)) {
                return (T) Boolean.TRUE;
            }
            if ("0".equals(normalized)) {
                return (T) Boolean.FALSE;
            }
            return (T) Boolean.valueOf(normalized);
        }
        if (targetType == BigDecimal.class) {
            return (T) (value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value)));
        }
        if (targetType == LocalDate.class) {
            return (T) (value instanceof LocalDate ? value : LocalDate.parse(String.valueOf(value)));
        }
        if (targetType == LocalDateTime.class) {
            return (T) (value instanceof LocalDateTime ? value : LocalDateTime.parse(String.valueOf(value)));
        }
        if (CrudRecord.class.isAssignableFrom(targetType)) {
            if (value instanceof CrudRecord) {
                return (T) value;
            }
            if (value instanceof Map<?, ?>) {
                return (T) copyOf((Map<String, Object>) value);
            }
        }
        if (Enum.class.isAssignableFrom(targetType)) {
            return (T) Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), String.valueOf(value));
        }
        throw new IllegalArgumentException("不支持的字段类型转换: " + targetType.getName());
    }
}
