package com.entloom.crud.annotations;

import com.entloom.crud.api.model.CommandAction;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 命令业务动作声明，路由与输入输出契约来自共享动作定义，并与处理器泛型校验。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudCommandAction {
    /** 动作中文名称，供文档和诊断使用。 */
    String name() default "";

    /** 动作所需能力码。 */
    String capability() default "";

    /** 允许的可信入口；空数组表示不限制。 */
    String[] accessEntries() default {};

    /** 允许的 portal；空数组表示不限制。 */
    String[] portals() default {};
    /** 与调用方共用的类型化动作定义；框架仅实例化定义，不提前创建 Handler。 */
    Class<? extends CommandAction<?, ?>> value();
}
