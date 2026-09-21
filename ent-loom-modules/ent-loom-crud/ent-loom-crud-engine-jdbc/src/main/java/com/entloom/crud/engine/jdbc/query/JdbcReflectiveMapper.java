package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.util.NamingUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 查询结果的反射映射与关系绑定器。
 */
public class JdbcReflectiveMapper {
    /** 值转换器。 */
    private final JdbcValueConverter valueConverter;
    /** 字段解析器。 */
    private final JdbcReflectionFieldResolver fieldResolver;
    /** 关联回填助手。 */
    private final JdbcRelationAssignmentHelper relationAssignmentHelper;
    /** 对象图遍历器。 */
    private final JdbcObjectGraphCollector objectGraphCollector;

    public JdbcReflectiveMapper() {
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
    public <R> R mapRow(Map<String, Object> row, Class<R> viewType) {
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
            R target = mapMutableBean(row, viewType);
            return target;
        } catch (NoSuchMethodException ex) {
            return mapConstructorProjection(row, viewType);
        } catch (Exception e) {
            throw mappingFailure(viewType, e);
        }
    }

    private <R> R mapMutableBean(Map<String, Object> row, Class<R> viewType) throws Exception {
        R target = viewType.getDeclaredConstructor().newInstance();
        for (Field field : fieldResolver.writableFields(viewType)) {
            String key = rowKey(row, field.getName());
            if (key == null) {
                continue;
            }
            field.set(target, valueConverter.adapt(field.getType(), row.get(key)));
        }
        return target;
    }

    private <R> R mapConstructorProjection(Map<String, Object> row, Class<R> viewType) {
        try {
            Constructor<?> constructor = projectionConstructor(row, viewType);
            Parameter[] parameters = constructor.getParameters();
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                String name = parameter.getName();
                if (viewType.isRecord()) {
                    name = viewType.getRecordComponents()[i].getName();
                }
                String key = rowKey(row, name);
                if (key == null) {
                    throw new IllegalStateException("查询结果缺少构造器参数列: " + name);
                }
                Object rawValue = row.get(key);
                if (rawValue == null && parameter.getType().isPrimitive()) {
                    throw new IllegalStateException("基本类型构造器参数不能接收 SQL NULL: " + name);
                }
                arguments[i] = valueConverter.adapt(parameter.getType(), rawValue);
            }
            constructor.setAccessible(true);
            @SuppressWarnings("unchecked")
            R target = (R) constructor.newInstance(arguments);
            return target;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw mappingFailure(viewType, ex);
        }
    }

    private Constructor<?> projectionConstructor(Map<String, Object> row, Class<?> viewType)
        throws NoSuchMethodException {
        if (viewType.isRecord()) {
            RecordComponent[] components = viewType.getRecordComponents();
            Class<?>[] parameterTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
            return viewType.getDeclaredConstructor(parameterTypes);
        }
        Constructor<?> selected = null;
        int selectedParameterCount = -1;
        for (Constructor<?> constructor : viewType.getDeclaredConstructors()) {
            Parameter[] parameters = constructor.getParameters();
            if (parameters.length == 0 || !hasNamedColumns(row, parameters)) {
                continue;
            }
            if (parameters.length > selectedParameterCount) {
                selected = constructor;
                selectedParameterCount = parameters.length;
            } else if (parameters.length == selectedParameterCount) {
                throw new IllegalStateException("查询结果构造器匹配不唯一: " + viewType.getName());
            }
        }
        if (selected == null) {
            throw new IllegalStateException(
                "查询结果类型必须有无参构造器，或提供带参数名且能匹配结果列的构造器: " + viewType.getName()
            );
        }
        return selected;
    }

    private boolean hasNamedColumns(Map<String, Object> row, Parameter[] parameters) {
        for (Parameter parameter : parameters) {
            if (!parameter.isNamePresent() || rowKey(row, parameter.getName()) == null) {
                return false;
            }
        }
        return true;
    }

    private String rowKey(Map<String, Object> row, String fieldName) {
        if (row.containsKey(fieldName)) {
            return fieldName;
        }
        String snakeName = NamingUtils.camelToSnake(fieldName);
        if (row.containsKey(snakeName)) {
            return snakeName;
        }
        for (String key : row.keySet()) {
            if (key.equalsIgnoreCase(fieldName) || key.equalsIgnoreCase(snakeName)) {
                return key;
            }
        }
        return null;
    }

    private IllegalStateException mappingFailure(Class<?> viewType, Exception cause) {
        if (cause instanceof IllegalStateException) {
            return (IllegalStateException) cause;
        }
        return new IllegalStateException("查询结果行映射失败，目标类型: " + viewType.getName(), cause);
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
