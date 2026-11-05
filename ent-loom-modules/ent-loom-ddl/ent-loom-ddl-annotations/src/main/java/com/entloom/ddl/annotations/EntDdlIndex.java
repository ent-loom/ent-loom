package com.entloom.ddl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体数据库索引声明；字段使用 Java 属性名。 */
@Repeatable(EntDdlIndexes.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntDdlIndex {
    String name() default "";
    String[] fields() default {};
    boolean unique() default false;
    String expression() default "";
}
