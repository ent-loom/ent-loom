package com.entloom.doc.annotations;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.contract.descriptor.EntRelationDescriptor;
import com.entloom.meta.enums.RelationCardinality;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段文档注解（用于前端基础实体文档）。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntDocField {

    // DOC 基础展示语义。

    /** 字段名称，空字符串表示使用默认命名策略。 */
    String name() default "";

    /** 字段描述 */
    String description() default "";

    /** 示例值 */
    String example() default "";

    /** 是否必填，UNSET 表示未显式设置。 */
    OptionalBoolean required() default OptionalBoolean.UNSET;

    /** 最大长度（-1 表示未知或不限制） */
    int maxLength() default -1;

    /** 最小长度（-1 表示未知或不限制） */
    int minLength() default -1;

    // 通用关系语义：与 EntRelation 对齐。

    /**
     * 目标实体所属服务。
     *
     * @see EntRelationDescriptor#targetService()
     */
    String targetService() default "";

    /**
     * 目标实体名。
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

    // DOC 专属关系展示语义。

    /** 目标实体展示名称（如 学生） */
    String targetEntityLabel() default "";

    /** 关联备注（关联规则/关联条件说明） */
    String relationRemark() default "";
}
