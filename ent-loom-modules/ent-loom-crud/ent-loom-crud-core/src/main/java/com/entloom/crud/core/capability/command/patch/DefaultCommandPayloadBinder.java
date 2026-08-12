package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * core 默认命令 payload 绑定器。
 */
public class DefaultCommandPayloadBinder implements CommandPayloadBinder, DefaultEntityPatch.ValueConverter {
    private static final String DEFAULT_ID_FIELD = "id";
    private static final String[] DATE_PATTERNS = new String[] {
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss"
    };

    @Override
    public <T> EntityPatch<T> bindEntityPatch(Object payload, Class<T> entityType, EntityMeta meta) {
        return bindEntityPatch(payload, entityType, meta, java.util.Collections.<String>emptySet());
    }

    @Override
    public <T> EntityPatch<T> bindEntityPatch(
        Object payload,
        Class<T> entityType,
        EntityMeta meta,
        Set<String> additionalPresentFields
    ) {
        Map<String, Object> rawValues = bindFieldMap(payload, null);
        Map<String, Object> fieldValues = recognizedFieldValues(rawValues, meta, additionalPresentFields);
        Object id = extractId(meta, rawValues);
        T entity = bindEntity(rawValues, entityType, meta, additionalPresentFields);

        Set<String> presentFields = new LinkedHashSet<String>(fieldValues.keySet());
        Set<String> persistableFields = new LinkedHashSet<String>();
        Map<String, Object> valuesForDelegate = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            String field = entry.getKey();
            if (isPersistableField(meta, field)) {
                persistableFields.add(field);
                valuesForDelegate.put(field, entry.getValue());
            }
        }
        return new DefaultEntityPatch<T>(
            entityType,
            entity,
            id,
            presentFields,
            persistableFields,
            fieldValues,
            valuesForDelegate,
            this
        );
    }

    @Override
    public <T> T bindEntity(Object payload, Class<T> entityType, EntityMeta meta) {
        return bindEntity(payload, entityType, meta, Collections.<String>emptySet());
    }

    @Override
    public <T> T bindEntity(Object payload, Class<T> entityType, EntityMeta meta, Set<String> additionalEntityFields) {
        if (payload == null) {
            return null;
        }
        if (entityType.isInstance(payload)) {
            return entityType.cast(payload);
        }
        Map<String, Object> rawValues = bindFieldMap(payload, null);
        try {
            T entity = entityType.newInstance();
            for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
                String fieldName = entry.getKey();
                Field field = findField(entityType, fieldName);
                if (field == null) {
                    continue;
                }
                field.setAccessible(true);
                field.set(entity, convertFieldValue(fieldName, entry.getValue(), field));
            }
            return entity;
        } catch (InstantiationException ex) {
            throw new ValidationException("实体无法实例化: " + entityType.getName());
        } catch (IllegalAccessException ex) {
            throw new ValidationException("实体字段写入失败: " + entityType.getName());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("实体字段类型转换失败: " + entityType.getName() + ", reason: " + ex.getMessage());
        }
    }

    @Override
    public Map<String, Object> bindFieldMap(Object payload, EntityMeta meta) {
        Map<String, Object> rawValues = toRawFieldMap(payload);
        if (meta == null) {
            return rawValues;
        }
        return recognizedFieldValues(rawValues, meta, java.util.Collections.<String>emptySet());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <V> V convert(String field, Object value, Class<V> targetType) {
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return targetType == null ? (V) value : targetType.cast(value);
        }
        if (isBlankString(value) && targetType != String.class && !targetType.isPrimitive()) {
            return null;
        }
        try {
            if (targetType == String.class) {
                return targetType.cast(String.valueOf(value));
            }
            if (targetType == Long.class || targetType == Long.TYPE) {
                return (V) Long.valueOf(value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)));
            }
            if (targetType == Integer.class || targetType == Integer.TYPE) {
                return (V) Integer.valueOf(value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value)));
            }
            if (targetType == Short.class || targetType == Short.TYPE) {
                return (V) Short.valueOf(value instanceof Number ? ((Number) value).shortValue() : Short.valueOf(String.valueOf(value)));
            }
            if (targetType == Byte.class || targetType == Byte.TYPE) {
                return (V) Byte.valueOf(value instanceof Number ? ((Number) value).byteValue() : Byte.valueOf(String.valueOf(value)));
            }
            if (targetType == Double.class || targetType == Double.TYPE) {
                return (V) Double.valueOf(value instanceof Number ? ((Number) value).doubleValue() : Double.valueOf(String.valueOf(value)));
            }
            if (targetType == Float.class || targetType == Float.TYPE) {
                return (V) Float.valueOf(value instanceof Number ? ((Number) value).floatValue() : Float.valueOf(String.valueOf(value)));
            }
            if (targetType == Boolean.class || targetType == Boolean.TYPE) {
                return (V) Boolean.valueOf(value instanceof Boolean ? ((Boolean) value).booleanValue() : Boolean.valueOf(String.valueOf(value)));
            }
            if (targetType == BigDecimal.class) {
                return (V) (value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value)));
            }
            if (targetType == BigInteger.class) {
                return (V) (value instanceof BigInteger ? value : new BigInteger(String.valueOf(value)));
            }
            if (targetType == Date.class) {
                return (V) convertToDate(value);
            }
            if (targetType.isEnum()) {
                return (V) Enum.valueOf((Class<Enum>) targetType.asSubclass(Enum.class), String.valueOf(value));
            }
            return (V) value;
        } catch (RuntimeException ex) {
            if (ex instanceof ValidationException) {
                throw ex;
            }
            throw new ValidationException("字段类型转换失败: " + field + " -> " + targetType.getName());
        }
    }

    public <T> List<T> bindEntityList(Object raw, Class<T> entityType, EntityMeta meta) {
        List<T> result = new ArrayList<T>();
        if (raw == null) {
            return result;
        }
        Collection<?> source = raw instanceof Collection<?> ? (Collection<?>) raw : Collections.singleton(raw);
        for (Object item : source) {
            if (item != null) {
                result.add(bindEntity(item, entityType, meta));
            }
        }
        return result;
    }

    private Object convertFieldValue(String fieldName, Object value, Field field) {
        Class<?> fieldType = field.getType();
        if (value == null) {
            return null;
        }
        if (Collection.class.isAssignableFrom(fieldType)) {
            return convertCollectionField(fieldName, value, field);
        }
        return convert(fieldName, value, fieldType);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object convertCollectionField(String fieldName, Object value, Field field) {
        if (!(value instanceof Collection<?>)) {
            throw new ValidationException("字段类型转换失败: " + fieldName + " -> " + field.getType().getName());
        }
        Class<?> elementType = resolveCollectionElementType(field);
        if (elementType == null) {
            return value;
        }
        List converted = new ArrayList();
        for (Object item : (Collection<?>) value) {
            if (item == null || elementType.isInstance(item)) {
                converted.add(item);
            } else if (item instanceof Map<?, ?> || item instanceof CrudRecord) {
                converted.add(bindEntity(item, elementType, null));
            } else {
                converted.add(convert(fieldName, item, elementType));
            }
        }
        return converted;
    }

    private Class<?> resolveCollectionElementType(Field field) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType)) {
            return null;
        }
        Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class<?>)) {
            return null;
        }
        return (Class<?>) arguments[0];
    }

    private Map<String, Object> toRawFieldMap(Object payload) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (payload == null) {
            return result;
        }
        if (payload instanceof CrudRecord) {
            result.putAll(((CrudRecord) payload).asMap());
            return result;
        }
        if (payload instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) payload).entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        for (Field field : getAllFields(payload.getClass())) {
            try {
                field.setAccessible(true);
                result.put(field.getName(), field.get(payload));
            } catch (IllegalAccessException ex) {
                throw new ValidationException("payload 字段读取失败: " + field.getName());
            }
        }
        return result;
    }

    private Map<String, Object> recognizedFieldValues(
        Map<String, Object> rawValues,
        EntityMeta meta,
        Set<String> additionalPresentFields
    ) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            if (isRecognizedBusinessField(meta, entry.getKey()) || isAdditionalPresentField(additionalPresentFields, entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private Object extractId(EntityMeta meta, Map<String, Object> values) {
        if (meta != null && meta.getIdField() != null && values.containsKey(meta.getIdField())) {
            return normalizeId(values.get(meta.getIdField()));
        }
        if (values.containsKey(DEFAULT_ID_FIELD)) {
            return normalizeId(values.get(DEFAULT_ID_FIELD));
        }
        return null;
    }

    private Object normalizeId(Object id) {
        if (isBlankString(id)) {
            return null;
        }
        return id;
    }

    private boolean isBlankString(Object value) {
        return value instanceof String && ((String) value).trim().isEmpty();
    }

    private boolean isRecognizedBusinessField(EntityMeta meta, String field) {
        if (field == null) {
            return false;
        }
        if (meta == null) {
            return true;
        }
        if (meta.getAllowedFields().contains(field)) {
            return true;
        }
        return isIdField(meta, field);
    }

    private boolean isAdditionalPresentField(Set<String> additionalPresentFields, String field) {
        return field != null && additionalPresentFields != null && additionalPresentFields.contains(field);
    }

    private boolean isPersistableField(EntityMeta meta, String field) {
        if (meta == null || field == null || !meta.getAllowedFields().contains(field) || isIdField(meta, field)) {
            return false;
        }
        EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
        return fieldMeta == null || !fieldMeta.isRelation();
    }

    private boolean isIdField(EntityMeta meta, String field) {
        return meta != null
            && field != null
            && (field.equals(meta.getIdField()) || (!DEFAULT_ID_FIELD.equals(meta.getIdField()) && DEFAULT_ID_FIELD.equals(field)));
    }

    private Date convertToDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("^-?\\d+$")) {
            return new Date(Long.valueOf(text));
        }
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
                dateFormat.setLenient(false);
                return dateFormat.parse(text);
            } catch (ParseException ignored) {
                // try next pattern
            }
        }
        throw new ValidationException("日期格式不支持: " + text);
    }

    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            for (Field field : declaredFields) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
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
}
