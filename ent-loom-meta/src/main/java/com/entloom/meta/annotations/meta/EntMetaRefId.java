package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.RefIdRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 引用 ID 字段的关联提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaRefId {
    /**
     * 引用字段角色。
     */
    RefIdRole value() default RefIdRole.UNSET;

    /**
     * 被引用实体所属服务。
     */
    String refService() default "";

    /**
     * 被引用实体名。
     */
    String refEntity() default "";

    /**
     * 被引用主字段名，默认使用 id。
     */
    String refField() default "id";
}
