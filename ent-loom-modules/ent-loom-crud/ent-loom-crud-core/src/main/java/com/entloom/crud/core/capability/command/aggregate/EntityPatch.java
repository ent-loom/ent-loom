package com.entloom.crud.core.capability.command.aggregate;

import com.entloom.crud.core.exception.ValidationException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 实体 PATCH 视图。
 *
 * @param <E> 实体类型
 */
public class EntityPatch<E> {
    private final Class<E> entityType;
    private final E entity;
    private final Object id;
    private final Set<String> presentFields;
    private final Map<String, Object> valuesForDelegate;

    public EntityPatch(
        Class<E> entityType,
        E entity,
        Object id,
        Set<String> presentFields,
        Map<String, Object> valuesForDelegate
    ) {
        this.entityType = entityType;
        this.entity = entity;
        this.id = id;
        this.presentFields = presentFields == null
            ? Collections.<String>emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<String>(presentFields));
        this.valuesForDelegate = valuesForDelegate == null
            ? Collections.<String, Object>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(valuesForDelegate));
    }

    public Class<E> getEntityType() {
        return entityType;
    }

    public E getEntity() {
        return entity;
    }

    public Object getId() {
        return id;
    }

    public Long getLongId() {
        if (id == null) {
            return null;
        }
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        return Long.valueOf(String.valueOf(id));
    }

    public Set<String> getPresentFields() {
        return presentFields;
    }

    public boolean hasField(String field) {
        return presentFields.contains(field);
    }

    public boolean isPersistableField(String field) {
        return valuesForDelegate.containsKey(field);
    }

    @SuppressWarnings("unchecked")
    public <V> V get(String field, Class<V> targetType) {
        Object value = fieldValue(field);
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return targetType == null ? (V) value : targetType.cast(value);
        }
        throw new ValidationException("字段类型不匹配: " + field);
    }

    /**
     * 框架内部或高级扩展用于透传默认写入引擎的字段集合。
     * 普通业务规则应优先使用 {@link #getEntity()}、{@link #hasField(String)} 和 {@link #getId()}。
     */
    public Map<String, Object> getValuesForDelegate() {
        return valuesForDelegate;
    }

    public Class<E> entityType() {
        return getEntityType();
    }

    public E entity() {
        return getEntity();
    }

    public Object id() {
        return getId();
    }

    public Long longId() {
        return getLongId();
    }

    public Set<String> presentFields() {
        return getPresentFields();
    }

    public Map<String, Object> valuesForDelegate() {
        return getValuesForDelegate();
    }

    private Object fieldValue(String field) {
        if (field == null || !presentFields.contains(field)) {
            return null;
        }
        if (valuesForDelegate.containsKey(field)) {
            return valuesForDelegate.get(field);
        }
        if (entity == null) {
            return null;
        }
        Field reflectedField = findField(entity.getClass(), field);
        if (reflectedField == null) {
            return null;
        }
        try {
            reflectedField.setAccessible(true);
            return reflectedField.get(entity);
        } catch (IllegalAccessException ex) {
            throw new ValidationException("字段读取失败: " + field);
        }
    }

    private Field findField(Class<?> type, String field) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(field);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
