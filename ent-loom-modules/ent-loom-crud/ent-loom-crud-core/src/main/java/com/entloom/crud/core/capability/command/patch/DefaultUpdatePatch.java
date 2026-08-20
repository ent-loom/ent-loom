package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.core.exception.ValidationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 默认不可变的 {@link UpdatePatch} 实现。
 *
 * @param <T> 实体类型
 */
public class DefaultUpdatePatch<T> implements UpdatePatch<T> {
    private final Class<T> entityType;
    private final T entity;
    private final Object id;
    private final Set<String> presentFields;
    private final Set<String> persistableFields;
    private final Map<String, Object> fieldValues;
    private final Map<String, Object> valuesForDelegate;
    private final ValueConverter valueConverter;

    public DefaultUpdatePatch(
        Class<T> entityType,
        T entity,
        Object id,
        Set<String> presentFields,
        Set<String> persistableFields,
        Map<String, Object> fieldValues,
        Map<String, Object> valuesForDelegate,
        ValueConverter valueConverter
    ) {
        this.entityType = entityType;
        this.entity = entity;
        this.id = id;
        this.presentFields = unmodifiableSet(presentFields);
        this.persistableFields = unmodifiableSet(persistableFields);
        this.fieldValues = unmodifiableMap(fieldValues);
        this.valuesForDelegate = unmodifiableMap(valuesForDelegate);
        this.valueConverter = valueConverter;
    }

    @Override
    public Class<T> getEntityType() {
        return entityType;
    }

    @Override
    public T getEntity() {
        return entity;
    }

    @Override
    public Object getId() {
        return id;
    }

    @Override
    public Long getLongId() {
        if (id == null) {
            return null;
        }
        if (id instanceof Number) {
            return Long.valueOf(((Number) id).longValue());
        }
        try {
            return Long.valueOf(String.valueOf(id));
        } catch (NumberFormatException ex) {
            throw new ValidationException("id 无法转换为 Long: " + id);
        }
    }

    @Override
    public Set<String> getPresentFields() {
        return presentFields;
    }

    @Override
    public Set<String> getPersistableFields() {
        return persistableFields;
    }

    @Override
    public Map<String, Object> getValuesForDelegate() {
        return valuesForDelegate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V get(String field) {
        return (V) fieldValues.get(field);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V get(String field, Class<V> targetType) {
        if (!fieldValues.containsKey(field)) {
            return null;
        }
        Object value = fieldValues.get(field);
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return targetType == null ? (V) value : targetType.cast(value);
        }
        if (valueConverter == null) {
            throw new ValidationException("字段类型不匹配: " + field);
        }
        return valueConverter.convert(field, value, targetType);
    }

    private static Set<String> unmodifiableSet(Set<String> source) {
        return source == null
            ? Collections.<String>emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<String>(source));
    }

    private static Map<String, Object> unmodifiableMap(Map<String, Object> source) {
        return source == null
            ? Collections.<String, Object>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(source));
    }

    public interface ValueConverter {
        <V> V convert(String field, Object value, Class<V> targetType);
    }
}
