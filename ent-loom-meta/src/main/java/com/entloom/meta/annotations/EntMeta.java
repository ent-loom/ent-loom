package com.entloom.meta.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用元信息注解，可作为字段或注解扩展元数据入口。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface EntMeta {
    /**
     * 元信息键。
     */
    String key() default "";

    /**
     * 元信息值。
     */
    String value() default "";

    /**
     * 多值元信息。
     */
    String[] values() default {};
}
