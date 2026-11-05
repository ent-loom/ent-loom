package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体授权的重复声明容器。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudGrants {
    EntCrudGrant[] value();
}
