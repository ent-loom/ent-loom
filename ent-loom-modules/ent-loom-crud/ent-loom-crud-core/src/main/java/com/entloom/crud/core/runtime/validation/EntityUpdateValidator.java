package com.entloom.crud.core.runtime.validation;

import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Map;

/** 校验更新中显式提供的字段；未提供字段不作为必填输入。 */
@FunctionalInterface
public interface EntityUpdateValidator {
    void validate(EntityMeta meta, Map<String, Object> values);
}
