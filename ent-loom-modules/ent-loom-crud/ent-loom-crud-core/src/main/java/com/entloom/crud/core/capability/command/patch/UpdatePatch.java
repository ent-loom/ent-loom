package com.entloom.crud.core.capability.command.patch;

import com.entloom.crud.core.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 普通单表局部更新的稳定强类型视图。
 *
 * @param <T> 实体类型
 */
public interface UpdatePatch<T> {
    /** 构造业务代码中的局部更新；字段、类型与版本规则仍由写入内核校验。 */
    static <T> Builder<T> builder(Class<T> entityType, Object id) {
        return new Builder<T>(entityType, id);
    }

    /** 显式 set 表示字段已提交，包括值为 null 的清空操作。 */
    final class Builder<T> {
        private final Class<T> entityType;
        private final Object id;
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();
        private Long expectedVersion;

        private Builder(Class<T> entityType, Object id) {
            this.entityType = Objects.requireNonNull(entityType, "Patch 实体类型不能为空");
            this.id = Objects.requireNonNull(id, "Patch 主键不能为空");
        }

        public Builder<T> set(String field, Object value) {
            if (field == null || field.trim().isEmpty() || !field.equals(field.trim())) {
                throw new ValidationException("Patch 字段名不能为空或包含首尾空格");
            }
            values.put(field, value);
            return this;
        }

        /** 乐观锁预期版本，不作为普通字段写入。 */
        public Builder<T> expectedVersion(Long version) {
            this.expectedVersion = version;
            return this;
        }

        public UpdatePatch<T> build() {
            if (values.isEmpty()) throw new ValidationException("UpdatePatch 不能为空");
            return new DefaultUpdatePatch<T>(entityType, null, id, expectedVersion,
                values.keySet(), values.keySet(), values, values, null);
        }
    }

    Class<T> getEntityType();

    T getEntity();

    Object getId();

    /** 可选的预期版本；实体存在 version 字段时由 DAO 强制要求。 */
    default Long getExpectedVersion() {
        return null;
    }

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

    default Long expectedVersion() {
        return getExpectedVersion();
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
