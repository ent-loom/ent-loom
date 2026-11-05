package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 限制字段的 CRUD 授权用途，不影响持久化、内部归属校验或普通 Java 访问。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntCrudFieldAccess {
    /** 仅允许的用途；与 exclude 互斥，不能放宽实体元数据的能力。 */
    EntCrudFieldUse[] include() default {};

    /** 排除的用途；ALL 表示排除所有用途，不能与其他用途混用。 */
    EntCrudFieldUse[] exclude() default {};
}
