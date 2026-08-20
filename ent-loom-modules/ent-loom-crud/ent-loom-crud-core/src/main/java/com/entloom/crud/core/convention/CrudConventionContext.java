package com.entloom.crud.core.convention;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * CRUD Convention 当前字段上下文。
 */
public final class CrudConventionContext {
    private final Class<?> entityClass;
    private final Field field;

    public CrudConventionContext(Class<?> entityClass, Field field) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass 不能为空");
        this.field = Objects.requireNonNull(field, "field 不能为空");
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public Field field() {
        return field;
    }
}
