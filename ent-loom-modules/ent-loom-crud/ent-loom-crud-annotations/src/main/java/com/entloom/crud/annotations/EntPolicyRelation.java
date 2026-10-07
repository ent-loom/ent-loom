package com.entloom.crud.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体策略中的关系角色与实体字段绑定。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface EntPolicyRelation {
    String role();

    String field();
}
