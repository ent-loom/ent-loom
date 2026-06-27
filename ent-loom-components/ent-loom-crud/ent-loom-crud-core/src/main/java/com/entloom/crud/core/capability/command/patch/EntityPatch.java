package com.entloom.crud.core.capability.command.patch;

import java.util.Map;
import java.util.Set;

/**
 * 通用实体字段 PATCH 视图。
 *
 * @param <T> 实体类型
 */
public interface EntityPatch<T> {
    Class<T> getEntityType();

    T getEntity();

    Object getId();

    Long getLongId();

    Set<String> getPresentFields();

    /**
     * 框架内部或高级扩展用于透传默认写入引擎的字段集合。
     * 普通业务规则应优先使用 {@link #getEntity()}、{@link #hasField(String)} 和 {@link #get(String, Class)}。
     */
    Map<String, Object> getValuesForDelegate();

    default Class<T> entityType() {
        return getEntityType();
    }

    default T entity() {
        return getEntity();
    }

    default Object id() {
        return getId();
    }

    default Long longId() {
        return getLongId();
    }

    default Set<String> presentFields() {
        return getPresentFields();
    }

    default boolean hasField(String field) {
        return getPresentFields().contains(field);
    }

    boolean isPersistableField(String field);

    <V> V get(String field, Class<V> targetType);

    /**
     * 框架内部或高级扩展用于透传默认写入引擎的字段集合。
     */
    default Map<String, Object> valuesForDelegate() {
        return getValuesForDelegate();
    }
}
