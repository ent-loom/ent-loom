package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 命令场景动作注册。
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

    /**
     * 请求 payload 类型。
     *
     * @return 请求类型
     */
    Class<?> requestType();

    /**
     * 响应 data 类型。
     *
     * @return 响应类型
     */
    Class<?> responseType();
}
