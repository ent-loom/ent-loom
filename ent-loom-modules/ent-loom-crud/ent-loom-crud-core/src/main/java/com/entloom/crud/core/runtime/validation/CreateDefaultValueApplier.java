package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/** 创建阶段默认值补齐器；只补缺失字段，不覆盖显式 null。 */
public final class CreateDefaultValueApplier {

    /** 为 Map 载荷补齐默认值。 */
    public void applyToValues(Map<String, Object> values, EntityMeta meta) {
        if (values == null || meta == null) {
            return;
        }
        for (EntityFieldMeta fieldMeta : meta.getFieldMetas().values()) {
            if (fieldMeta.getFieldName().equals(meta.getIdField())) {
                continue;
            }
            if (!values.containsKey(fieldMeta.getFieldName()) && fieldMeta.getCreateDefaultValue() != null) {
                values.put(fieldMeta.getFieldName(), fieldMeta.getCreateDefaultValue());
            }
        }
    }

    /** 为强类型实体补齐默认值，并维护请求字段集合。 */
    public void applyToEntity(Object entity, EntityMeta meta, Set<String> presentFields) {
        if (entity == null || meta == null || presentFields == null) {
            return;
        }
        for (EntityFieldMeta fieldMeta : meta.getFieldMetas().values()) {
            if (fieldMeta.getFieldName().equals(meta.getIdField())
                || presentFields.contains(fieldMeta.getFieldName())
                || fieldMeta.getCreateDefaultValue() == null) {
                continue;
            }
            Field field = findField(entity.getClass(), fieldMeta.getFieldName());
            if (field == null) {
                throw new ValidationException("默认值字段不存在: " + fieldMeta.getFieldName());
            }
            try {
                field.setAccessible(true);
                field.set(entity, convert(fieldMeta.getCreateDefaultValue(), field.getType()));
                presentFields.add(fieldMeta.getFieldName());
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                throw new ValidationException("默认值字段类型不匹配: " + fieldMeta.getFieldName());
            }
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Object value, Class<?> targetType) {
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == Boolean.class || targetType == Boolean.TYPE) {
            return value instanceof Boolean ? value : Boolean.valueOf(String.valueOf(value));
        }
        if (targetType == Long.class || targetType == Long.TYPE) {
            return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : Long.valueOf(String.valueOf(value));
        }
        if (targetType == Integer.class || targetType == Integer.TYPE) {
            return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : Integer.valueOf(String.valueOf(value));
        }
        if (targetType == Short.class || targetType == Short.TYPE) {
            return value instanceof Number ? Short.valueOf(((Number) value).shortValue()) : Short.valueOf(String.valueOf(value));
        }
        if (targetType == Byte.class || targetType == Byte.TYPE) {
            return value instanceof Number ? Byte.valueOf(((Number) value).byteValue()) : Byte.valueOf(String.valueOf(value));
        }
        if (targetType == Double.class || targetType == Double.TYPE) {
            return value instanceof Number ? Double.valueOf(((Number) value).doubleValue()) : Double.valueOf(String.valueOf(value));
        }
        if (targetType == Float.class || targetType == Float.TYPE) {
            return value instanceof Number ? Float.valueOf(((Number) value).floatValue()) : Float.valueOf(String.valueOf(value));
        }
        if (targetType == BigDecimal.class) {
            return value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value));
        }
        if (targetType == BigInteger.class) {
            return value instanceof BigInteger ? value : new BigInteger(String.valueOf(value));
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType.asSubclass(Enum.class), String.valueOf(value));
        }
        return value;
    }
}
