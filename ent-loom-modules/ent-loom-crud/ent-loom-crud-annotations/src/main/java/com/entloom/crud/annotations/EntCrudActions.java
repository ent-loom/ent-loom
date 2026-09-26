package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明实体允许的业务动作及各入口准入策略。
 *
 * <p>未声明时可由程序式策略配置准入；声明后按动作白名单约束，空集合关闭全部业务动作。
 * 同一动作可按不同入口重复声明，策略匹配后仍须通过用户权限判断。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudActions {
    /** 动作及入口策略。 */
    EntCrudAction[] value() default {};
}
