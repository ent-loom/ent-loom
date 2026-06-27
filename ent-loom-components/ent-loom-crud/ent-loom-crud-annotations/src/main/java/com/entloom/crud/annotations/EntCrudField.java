package com.entloom.crud.annotations;

import com.entloom.crud.api.enums.JoinType;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.enums.RelationCardinality;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级关系声明注解。
 *
 * <p>CRUD-only 时完整声明 CRUD 关系；Meta-first 时只声明 CRUD 覆盖项。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntCrudField {
    // EntRelationDescriptor 通用关系语义：与 EntRelation 对齐。

    /**
     * 目标实体所属服务。
     *
     * @see EntRelationDescriptor#targetService()
     */
    String targetService() default "";

    /**
     * 目标实体名，用于无法直接依赖实体 Class 的场景。
     *
     * @see EntRelationDescriptor#targetEntity()
     */
    String targetEntity() default "";

    /**
     * 当前实体字段名，空字符串表示使用被注解字段名。
     *
     * @see EntRelationDescriptor#sourceField()
     */
    String sourceField() default "";

    /**
     * 目标字段名。
     *
     * @see EntRelationDescriptor#targetField()
     */
    String targetField() default "id";

    /**
     * 关系基数。
     *
     * @see EntRelationDescriptor#cardinality()
     */
    RelationCardinality cardinality() default RelationCardinality.MANY_TO_ONE;

    // CRUD 专属关系选项。

    /**
     * 目标实体类型，CRUD 的类型安全入口。
     */
    Class<?> targetClass() default Void.class;

    /**
     * 关系作用域。
     *
     * <p>CRUD 专属执行策略。
     */
    RelationScope scope() default RelationScope.LOCAL_DB;

    /**
     * 默认连接类型。
     *
     * <p>CRUD 专属执行策略。
     */
    JoinType joinType() default JoinType.LEFT;
}
