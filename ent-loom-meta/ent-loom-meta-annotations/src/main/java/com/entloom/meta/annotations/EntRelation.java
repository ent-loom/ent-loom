package com.entloom.meta.annotations;

import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.enums.role.RefIdRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级关系元信息。
 *
 * <p>外键引用优先声明在 ID 属性上，例如 customerId；CRUD 可按名称与目标类型绑定 customer 单对象导航属性。
 * CRUD 可结合已注册的唯一反向引用和集合泛型补全展开映射；歧义或特殊连接由组件显式配置。
 * 关系元信息本身不触发加载或级联操作。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntRelation {
    /**
     * 本地目标实体类，例如 {@code @EntRelation(Customer.class)}；须声明 {@link EntEntity}。
     * 与 {@link #targetEntity()} 互斥。
     */
    Class<?> value() default Void.class;

    /**
     * 无本地实体类时使用的目标实体标识，与 {@link #value()} 互斥。
     */
    String targetEntity() default "";

    /**
     * 引用字段角色。
     */
    RefIdRole role() default RefIdRole.UNSET;

    /**
     * 目标实体所属服务；类引用默认复用目标实体的服务，不允许声明不一致的服务。
     */
    String targetService() default "";

    /**
     * 来源字段名，空字符串表示使用被注解字段名。
     */
    String sourceField() default "";

    /**
     * 目标字段名，默认使用 id。
     */
    String targetField() default "id";

    /**
     * 关系基数。
     */
    RelationCardinality cardinality() default RelationCardinality.MANY_TO_ONE;
}
