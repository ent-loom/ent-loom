package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体的身份授权；先受实体操作、字段上限约束，再应用数据范围。public 为匿名分支。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(EntCrudGrants.class)
public @interface EntCrudGrant {
    String[] identities();
    /** 允许的操作；空数组显式禁止此身份，且不再继承全局授权。 */
    String[] actions();
    EntCrudGrantScope scope() default EntCrudGrantScope.DEFAULT;
    /** DEFAULT 时覆盖全局归属字段；EQUAL 时指定固定条件字段。 */
    String scopeField() default "";
    /** EQUAL 的固定值，启动时按字段 Java 类型转换。 */
    String scopeValue() default "";
    /** 默认继承实体字段上限；false 时未列出的字段用途全部关闭。 */
    boolean inheritFields() default true;
    Fields[] fields() default {};

    /** 字段收窄；缺省作用于所有用途，统一规则与用途专属规则取交集。 */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Fields {
        EntCrudFieldUse[] use() default {EntCrudFieldUse.ALL};
        String[] value() default {};
        /** 从继承集合中排除字段；不能与 value 同时使用。 */
        String[] exclude() default {};
    }
}
