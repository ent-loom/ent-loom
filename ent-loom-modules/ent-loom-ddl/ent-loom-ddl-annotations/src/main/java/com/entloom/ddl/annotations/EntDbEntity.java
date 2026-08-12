package com.entloom.ddl.annotations;

import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.ddl.enums.NamingStrategy;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体的数据库映射定义。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntDbEntity {
    /**
     * 物理表名。空值表示按命名策略推导。
     */
    String table() default "";

    /**
     * schema 名称，空值表示使用默认 schema。
     */
    String schema() default "";

    /**
     * 表注释。
     */
    String comment() default "";

    /**
     * 表名推导策略。
     */
    NamingStrategy namingStrategy() default NamingStrategy.SNAKE_CASE;

    /**
     * 表规模设定，用于风险策略。
     */
    DdlTableSize size() default DdlTableSize.UNSET;

}
