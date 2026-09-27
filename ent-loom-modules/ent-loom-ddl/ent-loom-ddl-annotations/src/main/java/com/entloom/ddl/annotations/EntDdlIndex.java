package com.entloom.ddl.annotations;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.ddl.enums.IndexType;
import com.entloom.ddl.enums.UniqueScope;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅在实体类型上声明的 DDL 索引定义。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(EntDdlIndexes.class)
public @interface EntDdlIndex {
    /**
     * 索引名。空值表示生成器推导。
     */
    String name() default "";

    /**
     * 索引的 Java 属性名列表，按顺序映射为持久化列；不接受数据库列名。
     * 与 expression 必须且只能填写一个。
     */
    String[] fields() default {};

    /**
     * 原生 SQL 表达式索引定义，使用数据库列名，不进行属性名转换。
     * 与 fields 必须且只能填写一个。
     */
    String expression() default "";

    /**
     * 是否唯一索引。UNSET 表示按策略推导。
     */
    OptionalBoolean unique() default OptionalBoolean.UNSET;

    /**
     * 唯一约束作用范围。仅在唯一索引场景下生效。
     */
    UniqueScope uniqueScope() default UniqueScope.ALL_ROWS;

    /**
     * 索引类型。
     */
    IndexType type() default IndexType.BTREE;

}
