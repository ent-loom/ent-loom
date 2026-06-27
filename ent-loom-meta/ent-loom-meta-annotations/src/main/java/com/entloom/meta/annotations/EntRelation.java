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
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntRelation {
    /**
     * 引用字段角色。
     */
    RefIdRole role() default RefIdRole.UNSET;

    /**
     * 目标实体所属服务。
     */
    String targetService() default "";

    /**
     * 目标实体名。
     */
    String targetEntity() default "";

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
