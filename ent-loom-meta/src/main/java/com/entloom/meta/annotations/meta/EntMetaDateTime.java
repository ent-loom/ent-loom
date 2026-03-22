package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.DateTimeRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日期时间字段的补充约束与存储提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaDateTime {
    /**
     * 日期时间字段角色。
     */
    DateTimeRole value() default DateTimeRole.UNSET;

    /**
     * 自动填充策略。
     */
    AutoFill autoFill() default AutoFill.UNSET;

    /**
     * 存储格式，UNSET 表示由框架推断。
     */
    TimeEncoding encoding() default TimeEncoding.UNSET;

    /**
     * 时区标识（例如 Asia/Shanghai），空字符串表示未指定。
     */
    String timezone() default "";

    /**
     * 日期时间存储方式。
     */
    enum TimeEncoding {
        /** 未设置。 */
        UNSET,
        /** 原生日期时间类型。 */
        ISO_LOCAL,
        /** UTC timeline。 */
        INSTANT,
        /** 以毫秒时间戳存储。 */
        EPOCH_MILLIS,
        /** 以秒时间戳存储。 */
        EPOCH_SECONDS
    }

    /**
     * 日期时间自动填充策略。
     */
    enum AutoFill {
        /** 未设置。 */
        UNSET,
        /** 不自动填充。 */
        NONE,
        /** 仅创建时填充。 */
        CREATED,
        /** 仅更新时填充。 */
        UPDATED,
        /** 创建和更新时都填充。 */
        CREATED_UPDATED
    }
}
