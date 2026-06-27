package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.JsonRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSON 字段的结构和校验提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaJson {
    /**
     * JSON 字段角色。
     */
    JsonRole value() default JsonRole.UNSET;

    /**
     * 校验强度模式。
     */
    ValidateMode validateMode() default ValidateMode.UNSET;

    /**
     * JSON Schema 引用地址或标识。
     */
    String schemaRef() default "";

    /**
     * 最大字节数，-1 表示不限制。
     */
    long maxBytes() default -1L;

    /**
     * JSON 校验模式。
     */
    enum ValidateMode {
        /** 未设置。 */
        UNSET,
        /** 不校验。 */
        NONE,
        /** 基础校验。 */
        BASIC,
        /** 严格校验。 */
        STRICT
    }
}
