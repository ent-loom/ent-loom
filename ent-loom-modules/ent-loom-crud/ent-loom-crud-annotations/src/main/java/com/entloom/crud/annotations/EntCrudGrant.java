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
    /** 覆盖默认身份归属字段；与 where 同时声明时取交集。 */
    String scopeField() default "";
    /**
     * 仅用于 view 的静态 SQL 行条件，使用当前实体表的数据库列名，不写 WHERE 关键字。
     * 不解析、不绑定动态参数；不得拼接外部输入。始终与基础范围取交集，跨归属查看需显式声明 scope = ALL。
     * JDBC 执行时整体加括号，与强制治理条件取交集。
     */
    String where() default "";
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
