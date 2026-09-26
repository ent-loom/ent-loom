package com.entloom.crud.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体动作在一个业务入口下的准入声明；不授予当前用户执行权限。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface EntCrudAction {
    /** 动作标识，与 Handler 的 scene 对应。 */
    String value();

    /** 动作中文名称，同一实体动作在各入口下必须一致。 */
    String name();

    /** 业务入口，按完整策略键精确匹配，不向 base 回退。 */
    String accessEntry() default "base";

    /** 交给权限服务的业务权限码。 */
    String capability();

    /** 允许的可信入口形态；空集合表示不额外限制。 */
    String[] portals() default {};
}
