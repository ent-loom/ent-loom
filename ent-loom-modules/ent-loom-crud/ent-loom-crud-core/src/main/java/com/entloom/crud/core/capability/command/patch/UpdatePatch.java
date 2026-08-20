package com.entloom.crud.core.capability.command.patch;

import java.util.Map;
import java.util.Set;

/**
 * 普通单表局部更新的稳定强类型视图。
 *
 * @param <T> 实体类型
 */
public interface UpdatePatch<T> {
    Class<T> getEntityType();

    T getEntity();

    Object getId();

    /**
     * 数字主键的 Long 便捷视图。
     */
    Long getLongId();

    Set<String> getPresentFields();

    Set<String> getPersistableFields();

    /**
     * 框架内部或高级扩展用于透传默认写入引擎的字段集合。
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

    default Set<String> presentFields() {
        return getPresentFields();
    }

    default Set<String> persistableFields() {
        return getPersistableFields();
    }

    default boolean hasField(String field) {
        return getPresentFields().contains(field);
    }

    default boolean isPersistableField(String field) {
        return getPersistableFields().contains(field);
    }

    /**
     * 读取已识别字段的绑定值；字段未出现时返回 null。
     */
    <V> V get(String field);

    /**
     * 读取字段并执行显式目标类型转换。
     */
    <V> V get(String field, Class<V> targetType);

    default Map<String, Object> valuesForDelegate() {
        return getValuesForDelegate();
    }
}
