package com.entloom.crud.starter.dao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/** 显式指定 DAO 扫描包，替代默认应用包扫描；无参数时扫描声明类所在包。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(EntDaoScanRegistrar.class)
public @interface EntDaoScan {
    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};
}
