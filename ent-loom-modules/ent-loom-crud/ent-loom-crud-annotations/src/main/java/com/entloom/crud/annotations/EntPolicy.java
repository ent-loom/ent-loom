package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为已注册实体或分类接口选择项目策略模板；需要显式规则差异的实体改用 Java 绑定。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntPolicy {
    String template();

    EntPolicyRelation[] relations() default {};
}
