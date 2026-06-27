package com.entloom.meta.annotations.meta;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.enums.role.TextRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 文本字段的长度、格式和脱敏提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaText {
    /**
     * 文本字段角色。
     */
    TextRole value() default TextRole.UNSET;

    /**
     * 是否允许多行，UNSET 表示未显式指定。
     */
    OptionalBoolean multiline() default OptionalBoolean.UNSET;

    /**
     * 最大长度，-1 表示不限制。
     */
    int maxLength() default -1;

    /**
     * 正则校验表达式，空字符串表示不校验。
     */
    String pattern() default "";

    /**
     * 脱敏策略。
     */
    Masking masking() default Masking.UNSET;

    /**
     * 文本脱敏模式。
     */
    enum Masking {
        /** 未设置。 */
        UNSET,
        /** 不脱敏。 */
        NONE,
        /** 局部脱敏。 */
        PARTIAL,
        /** 全量脱敏。 */
        FULL,
        /** 哈希化。 */
        HASH
    }
}
