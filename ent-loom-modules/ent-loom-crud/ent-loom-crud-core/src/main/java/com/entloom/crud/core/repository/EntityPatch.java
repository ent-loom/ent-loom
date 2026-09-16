package com.entloom.crud.core.repository;

import com.entloom.crud.core.exception.ValidationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体局部更新内容，显式区分“未提供字段”和“将字段更新为 null”。
 *
 * @param <T> 实体类型
 */
public final class EntityPatch<T> {
    /** 待更新字段及其值。 */
    private final Map<String, Object> values;

    private EntityPatch(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public Map<String, Object> getValues() {
        return values;
    }

    /** 实体局部更新构建器。 */
    public static final class Builder<T> {
        /** 按加入顺序保存更新字段。 */
        private final Map<String, Object> values = new LinkedHashMap<String, Object>();

        public Builder<T> set(String field, Object value) {
            if (field == null || field.trim().isEmpty()) {
                throw new ValidationException("更新字段不能为空");
            }
            values.put(field.trim(), value);
            return this;
        }

        public EntityPatch<T> build() {
            if (values.isEmpty()) {
                throw new ValidationException("更新内容不能为空");
            }
            return new EntityPatch<T>(values);
        }
    }
}
