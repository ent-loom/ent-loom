package com.entloom.crud.annotations;

import com.entloom.crud.enums.QueryStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注册查询处理器。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudQueryHandler {
    /**
     * 有序实体范围，首位为主实体。
     *
     * @return 实体范围
     */
    Class<?>[] entityClasses();

    /**
     * 处理器声明支持的 scene 列表，必须显式声明。
     *
     * @return scene 列表
     */
    String[] scenes();

    /**
     * 默认查询策略覆盖。
     *
     * @return 查询策略
     */
    QueryStrategy defaultStrategy() default QueryStrategy.DEFAULT;
}
