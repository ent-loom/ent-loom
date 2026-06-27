package com.entloom.meta.annotations.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 富文本字段的格式和清洗策略提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaRichContent {
    /**
     * 富文本格式。
     */
    Format format() default Format.UNSET;

    /**
     * 清洗策略。
     */
    SanitizePolicy sanitizePolicy() default SanitizePolicy.UNSET;

    /**
     * 最大内容长度，-1 表示不限制。
     */
    int maxLength() default -1;

    /**
     * 富文本格式类型。
     */
    enum Format {
        /** 未设置。 */
        UNSET,
        /** HTML。 */
        HTML,
        /** Markdown。 */
        MARKDOWN
    }

    /**
     * 内容清洗策略。
     */
    enum SanitizePolicy {
        /** 未设置。 */
        UNSET,
        /** 不清洗。 */
        NONE,
        /** 基础清洗。 */
        BASIC,
        /** 严格清洗。 */
        STRICT
    }
}
