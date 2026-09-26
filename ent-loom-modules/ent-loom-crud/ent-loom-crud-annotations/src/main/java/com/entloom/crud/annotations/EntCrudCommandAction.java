package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 命令场景动作路由声明，请求与响应类型由处理器泛型确定。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudCommandAction {
    /**
     * 目标实体类型。
     *
     * @return 实体类型
     */
    Class<?> entityClass();

    /**
     * 场景稳定 code。
     *
     * @return 场景码
     */
    String scene();
}
