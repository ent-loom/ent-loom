package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.model.CrudRecord;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象图遍历器，用于从根对象集合中收集目标类型对象。
 */
final class JdbcObjectGraphCollector {
    private final JdbcReflectionFieldResolver fieldResolver;

    JdbcObjectGraphCollector(JdbcReflectionFieldResolver fieldResolver) {
        this.fieldResolver = fieldResolver;
    }

    /**
     * 深度遍历对象图并收集匹配类型对象。
     */
    void collectObjectsOfType(
        Object current,
        Class<?> targetType,
        List<Object> matches,
        IdentityHashMap<Object, Boolean> visited
    ) {
        if (current == null || targetType == null || visited.put(current, Boolean.TRUE) != null) {
            return;
        }
        if (targetType.isInstance(current)) {
            matches.add(current);
        }
        if (current instanceof CrudRecord) {
            for (Object value : ((CrudRecord) current).asMap().values()) {
                collectObjectsOfType(value, targetType, matches, visited);
            }
            return;
        }
        if (current instanceof Map<?, ?>) {
            for (Object value : ((Map<?, ?>) current).values()) {
                collectObjectsOfType(value, targetType, matches, visited);
            }
            return;
        }
        if (current instanceof Collection<?>) {
            for (Object value : (Collection<?>) current) {
                collectObjectsOfType(value, targetType, matches, visited);
            }
            return;
        }
        if (isSimpleValue(current.getClass())) {
            return;
        }
        for (Field field : fieldResolver.writableFields(current.getClass())) {
            try {
                collectObjectsOfType(field.get(current), targetType, matches, visited);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("无法遍历字段 " + field.getName(), e);
            }
        }
    }

    private boolean isSimpleValue(Class<?> type) {
        return type.isPrimitive()
            || Number.class.isAssignableFrom(type)
            || CharSequence.class.isAssignableFrom(type)
            || Boolean.class == type
            || Character.class == type
            || java.util.Date.class.isAssignableFrom(type)
            || type.isEnum();
    }
}
