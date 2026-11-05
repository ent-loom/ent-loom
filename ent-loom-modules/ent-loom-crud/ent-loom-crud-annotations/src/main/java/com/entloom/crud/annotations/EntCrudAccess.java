package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体身份分支的静态授权差异；动态范围与事实仍由 Java 授权服务提供。 */
@Repeatable(EntCrudAccesses.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudAccess {
    String identity();
    /** 是否显式设置动作；用于区分空列表撤权与省略继承。 */
    boolean actionsSpecified() default false;
    String[] actions() default {};
    String[] addActions() default {};
    String[] removeActions() default {};
    String scope() default "";
    /** 简单规则，格式为 field=value；字符串值不需要额外引号。 */
    String[] rules() default {};
    EntCrudAccessField[] fields() default {};
}
