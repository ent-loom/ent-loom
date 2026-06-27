package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.util.NamingUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 查询结果的反射映射与关系绑定器。
 */
class JdbcReflectiveMapper {
    /** 值转换器。 */
    private final JdbcValueConverter valueConverter;
    /** 字段解析器。 */
    private final JdbcReflectionFieldResolver fieldResolver;
    /** 关联回填助手。 */
    private final JdbcRelationAssignmentHelper relationAssignmentHelper;
    /** 对象图遍历器。 */
    private final JdbcObjectGraphCollector objectGraphCollector;

    JdbcReflectiveMapper() {
        this(new JdbcValueConverter(), false);
    }

    JdbcReflectiveMapper(JdbcValueConverter valueConverter) {
        this(valueConverter, false);
    }

    JdbcReflectiveMapper(boolean relationFieldFallbackEnabled) {
        this(new JdbcValueConverter(), relationFieldFallbackEnabled);
    }

    JdbcReflectiveMapper(JdbcValueConverter valueConverter, boolean relationFieldFallbackEnabled) {
        this.valueConverter = valueConverter == null ? new JdbcValueConverter() : valueConverter;
        this.fieldResolver = new JdbcReflectionFieldResolver();
        this.relationAssignmentHelper = new JdbcRelationAssignmentHelper(fieldResolver, relationFieldFallbackEnabled);
        this.objectGraphCollector = new JdbcObjectGraphCollector(fieldResolver);
    }

    /**
     * 将结果行映射为目标对象。
     */
    @SuppressWarnings("unchecked")
    <R> R mapRow(Map<String, Object> row, Class<R> viewType) {
        if (CrudRecord.class.isAssignableFrom(viewType)) {
            return (R) CrudRecord.copyOf(row);
        }
        if (Map.class.isAssignableFrom(viewType)) {
            if (viewType.isInstance(row)) {
                return (R) row;
            }
            if (!viewType.isInterface()) {
                try {
                    Map<String, Object> target = (Map<String, Object>) viewType.getDeclaredConstructor().newInstance();
                    target.putAll(row);
                    return (R) target;
                } catch (Exception e) {
                    throw new IllegalStateException("查询结果行映射失败，目标类型: " + viewType.getName(), e);
                }
            }
            return (R) row;
        }
        try {
            R target = viewType.getDeclaredConstructor().newInstance();
            for (Field field : fieldResolver.writableFields(viewType)) {
                String key = NamingUtils.camelToSnake(field.getName());
                Object value;
                if (row.containsKey(key)) {
                    value = row.get(key);
                } else if (row.containsKey(field.getName())) {
                    value = row.get(field.getName());
                } else {
                    continue;
                }
                field.set(target, valueConverter.adapt(field.getType(), value));
            }
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("查询结果行映射失败，目标类型: " + viewType.getName(), e);
        }
    }

    List<Object> extractFieldValues(List<?> objects, String fieldName) {
        List<Object> values = new ArrayList<Object>();
        for (Object object : objects) {
            Object value = readField(object, fieldName);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    List<Object> collectObjectsOfType(List<?> roots, Class<?> targetType) {
        List<Object> matches = new ArrayList<Object>();
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<Object, Boolean>();
        if (roots == null || targetType == null) {
            return matches;
        }
        for (Object root : roots) {
            objectGraphCollector.collectObjectsOfType(root, targetType, matches, visited);
        }
        return matches;
    }

    /**
     * 读取对象字段值。
     */
    Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        if (target instanceof CrudRecord) {
            return readMapValue(((CrudRecord) target).asMap(), fieldName);
        }
        if (target instanceof Map<?, ?>) {
            return readMapValue((Map<?, ?>) target, fieldName);
        }
        Optional<Field> optionalField = fieldResolver.resolveField(target.getClass(), fieldName);
        if (!optionalField.isPresent()) {
            return null;
        }
        try {
            return optionalField.get().get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法读取字段 " + fieldName, e);
        }
    }

    /**
     * 将子对象集合分配到根对象的关联字段。
     */
    Object assignChildren(Object root, RelationEdge edge, List<Object> children) {
        return relationAssignmentHelper.assignChildren(root, edge, children);
    }

    private Object readMapValue(Map<?, ?> map, String fieldName) {
        if (map.containsKey(fieldName)) {
            return map.get(fieldName);
        }
        String snakeName = NamingUtils.camelToSnake(fieldName);
        if (map.containsKey(snakeName)) {
            return map.get(snakeName);
        }
        return null;
    }
}
