package com.entloom.meta.annotations.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 主键字段的生成与约束提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaId {
    /**
     * 主键生成策略。
     */
    IdGenerator generator() default IdGenerator.UNSET;

    /**
     * 主键生成器类型。
     */
    enum IdGenerator {
        /** 未设置。 */
        UNSET,
        /** 由框架自动决定。 */
        AUTO,
        /** 雪花算法。 */
        SNOWFLAKE,
        /** UUID。 */
        UUID,
        /** 自定义实现。 */
        CUSTOM
    }
}
