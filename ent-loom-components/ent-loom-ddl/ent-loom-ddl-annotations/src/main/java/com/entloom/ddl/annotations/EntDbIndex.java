package com.entloom.ddl.annotations;

import com.entloom.base.common.OptionalBoolean;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体数据库索引定义。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@Repeatable(EntDbIndexes.class)
public @interface EntDbIndex {
    /**
     * 索引名。空值表示生成器推导。
     */
    String name() default "";

    /**
     * 索引字段列表，按顺序生效。
     * 字段级使用时可留空，表示当前字段参与单列索引。
     */
    String[] fields() default {};

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

    enum UniqueScope {
        ALL_ROWS,
        ACTIVE_ONLY
    }

    enum IndexType {
        BTREE,
        HASH,
        FULLTEXT
    }
}
