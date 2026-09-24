package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 覆盖实体允许的业务动作场景。
 *
 * <p>未声明时沿用项目全局权限；声明后按白名单处理，空白名单表示关闭全部业务动作。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudActions {
    /** 允许的业务动作场景，如 place、cancel。 */
    String[] value() default {};
}
