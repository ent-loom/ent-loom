package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.FlagRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 布尔标记字段的补充提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaFlag {
    /**
     * 标记字段角色。
     */
    FlagRole value() default FlagRole.UNSET;
}
