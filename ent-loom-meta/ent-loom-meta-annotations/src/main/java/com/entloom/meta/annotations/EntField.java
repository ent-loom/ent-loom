package com.entloom.meta.annotations;

import com.entloom.base.util.value.TypedValueType;
import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.enums.EntFieldKind;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明字段在属性系统中的展示名称、基础类型及约束。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntField {
    /**
     * 字段展示名称，空字符串表示使用默认命名策略。
     */
    String value() default "";

    /**
     * 属性类型枚举，AUTO 表示按字段类型和命名约定推断。
     */
    EntFieldKind kind() default EntFieldKind.AUTO;

    /**
     * 字段说明文本，用于文档或 UI 提示。
     */
    String description() default "";

    /**
     * 示例值列表，用于文档展示、接口示例和表单提示。
     */
    String[] examples() default {};

    /**
     * 创建时业务默认值，不等同于数据库默认值（DDL DEFAULT）。
     */
    String createDefaultValue() default "";

    /**
     * createDefaultValue 的解析类型，UNSET 表示按字段 Java 类型和 EntFieldKind 推断。
     */
    TypedValueType createDefaultValueType() default TypedValueType.UNSET;

    /**
     * 业务字段必填约束；UNSET 表示未显式声明，并兼容从 Validation 默认组推断。
     * 字段类型只决定校验方式，不决定是否必填；该属性不表达数据库非空或响应字段存在性。
     */
    OptionalBoolean required() default OptionalBoolean.UNSET;

    /**
     * 是否只读，UNSET 表示由角色和上下文策略决定。
     */
    OptionalBoolean readOnly() default OptionalBoolean.UNSET;
}
