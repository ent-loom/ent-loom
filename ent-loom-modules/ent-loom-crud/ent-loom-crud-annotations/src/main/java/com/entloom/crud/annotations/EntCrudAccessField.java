package com.entloom.crud.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/** 身份分支字段用途差异。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface EntCrudAccessField {
    String use();
    String mode() default "EXCLUDE";
    String[] fields() default {};
}
