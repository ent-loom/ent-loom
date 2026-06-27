package com.entloom.doc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体文档注解（用于前端基础实体文档）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntDocEntity {

    /** 实体名称 */
    String name();

    /** 实体描述 */
    String description() default "";

    /** 实体示例（可选） */
    String example() default "";
}
