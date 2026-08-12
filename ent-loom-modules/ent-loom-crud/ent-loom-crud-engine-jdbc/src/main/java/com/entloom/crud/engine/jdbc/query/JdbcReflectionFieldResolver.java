package com.entloom.crud.engine.jdbc.query;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 反射字段解析器，负责字段查找与缓存。
 */
final class JdbcReflectionFieldResolver {
    /** 可写字段缓存。 */
    private final ConcurrentMap<Class<?>, List<Field>> writableFieldsCache = new ConcurrentHashMap<Class<?>, List<Field>>();
    /** 字段名查询缓存。 */
    private final ConcurrentMap<Class<?>, ConcurrentMap<String, Optional<Field>>> fieldLookupCache =
        new ConcurrentHashMap<Class<?>, ConcurrentMap<String, Optional<Field>>>();
    /** 关联字段缓存。 */
    private final ConcurrentMap<RelationKey, Optional<Field>> relationFieldCache =
        new ConcurrentHashMap<RelationKey, Optional<Field>>();

    /**
     * 获取类型可写字段列表（含父类）。
     */
    List<Field> writableFields(Class<?> type) {
        return writableFieldsCache.computeIfAbsent(type, key -> {
            List<Field> fields = new ArrayList<Field>();
            Class<?> current = key;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    fields.add(field);
                }
                current = current.getSuperclass();
            }
            return fields;
        });
    }

    /**
     * 解析字段名。
     */
    Optional<Field> resolveField(Class<?> rootType, String fieldName) {
        ConcurrentMap<String, Optional<Field>> classCache = fieldLookupCache.computeIfAbsent(
            rootType,
            key -> new ConcurrentHashMap<String, Optional<Field>>()
        );
        return classCache.computeIfAbsent(fieldName, key -> {
            Class<?> current = rootType;
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return Optional.empty();
        });
    }

    /**
     * 按目标子类型推断关联字段。
     */
    Optional<Field> resolveRelationField(Class<?> rootType, Class<?> childType) {
        RelationKey key = new RelationKey(rootType, childType);
        return relationFieldCache.computeIfAbsent(key, ignored -> {
            for (Field field : writableFields(rootType)) {
                if (Collection.class.isAssignableFrom(field.getType()) && isCollectionElementType(field, childType)) {
                    return Optional.of(field);
                }
                if (Objects.equals(field.getType(), childType)) {
                    return Optional.of(field);
                }
            }
            return Optional.empty();
        });
    }

    private boolean isCollectionElementType(Field field, Class<?> childType) {
        Type type = field.getGenericType();
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] args = parameterizedType.getActualTypeArguments();
        return args.length == 1 && Objects.equals(args[0], childType);
    }

    private static final class RelationKey {
        private final Class<?> rootType;
        private final Class<?> childType;

        private RelationKey(Class<?> rootType, Class<?> childType) {
            this.rootType = rootType;
            this.childType = childType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RelationKey)) {
                return false;
            }
            RelationKey that = (RelationKey) other;
            return Objects.equals(rootType, that.rootType) && Objects.equals(childType, that.childType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rootType, childType);
        }
    }
}
