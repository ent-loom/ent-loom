package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.EnumRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 枚举字段的补充约束与展示提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaEnum {
    /**
     * 枚举字段角色。
     */
    EnumRole value() default EnumRole.UNSET;

    /**
     * 绑定的枚举类，UnspecifiedEnum.class 表示未指定（可由字段类型推断）。
     */
    Class<? extends Enum<?>> enumClass() default UnspecifiedEnum.class;

    /**
     * 枚举值类型（语义层），由 schema 层决定具体物理存储。
     */
    ValueType valueType() default ValueType.UNSET;

    /**
     * 选择基数（单选/多选）。
     */
    Cardinality cardinality() default Cardinality.UNSET;

    /**
     * 多选模式下允许的最大选择数，-1 表示不限制。
     */
    int maxSelections() default -1;

    /**
     * 枚举值类型。
     */
    enum ValueType {
        /** 未设置。 */
        UNSET,
        /** 以整数值表达。 */
        INT,
        /** 以字符串值表达。 */
        STRING
    }

    /**
     * 枚举选择基数。
     */
    enum Cardinality {
        /** 未设置。 */
        UNSET,
        /** 单选。 */
        SINGLE,
        /** 多选。 */
        MULTI
    }

    /**
     * 枚举类未显式指定时的占位值。
     */
    enum UnspecifiedEnum {
        /** 占位项。 */
        VALUE
    }
}
