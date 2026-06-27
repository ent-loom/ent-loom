package com.entloom.meta.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明实体索引元信息。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(EntIndexes.class)
public @interface EntIndex {
    /**
     * 索引名，空字符串表示由框架生成。
     */
    String name() default "";

    /**
     * 索引字段列表，按顺序生效。
     */
    String[] fields();

    /**
     * 是否唯一索引。
     */
    boolean unique() default false;
}
