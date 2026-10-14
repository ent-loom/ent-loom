package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为已注册实体或项目分类接口选择策略模板。
 *
 * <p>选择 YAML 模板时，复用标准操作缺省、关系约定及实体字段授权，无需再次登记 YAML 绑定；
 * Java 模板的执行声明仍由项目提供。需要显式规则差异的实体改用 Java 绑定。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudPolicy {
    String template();

    EntPolicyRelation[] relations() default {};
}
