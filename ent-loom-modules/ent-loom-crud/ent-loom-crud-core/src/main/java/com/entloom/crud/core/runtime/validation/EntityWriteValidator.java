package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Map;

/** 更新只校验显式字段，创建校验默认值和归属填充后的完整实体。 */
@FunctionalInterface
public interface EntityWriteValidator {
    void validate(EntityMeta meta, Map<String, Object> values);

    /** 创建时校验完成默认值和归属填充后的完整实体。 */
    default void validateCreate(EntityMeta meta, Map<String, Object> values) {
        validate(meta, values);
    }
}
