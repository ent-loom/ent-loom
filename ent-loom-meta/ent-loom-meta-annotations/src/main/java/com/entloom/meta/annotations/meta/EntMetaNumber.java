package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.NumberRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数值字段的精度与范围提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaNumber {
    /**
     * 数值字段角色。
     */
    NumberRole value() default NumberRole.UNSET;

    /**
     * 总精度（总位数），-1 表示未限制。
     */
    int precision() default -1;

    /**
     * 小数位数，-1 表示未限制。
     */
    int scale() default -1;

    /**
     * 最小值（字符串表示以兼容大数）。
     */
    String min() default "";

    /**
     * 最大值（字符串表示以兼容大数）。
     */
    String max() default "";

    /**
     * 步长（字符串表示）。
     */
    String step() default "";

    /**
     * 单位描述，例如 kg、CNY。
     */
    String unit() default "";
}
