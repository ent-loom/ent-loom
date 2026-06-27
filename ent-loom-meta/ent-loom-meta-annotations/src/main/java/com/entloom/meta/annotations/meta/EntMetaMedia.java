package com.entloom.meta.annotations.meta;

import com.entloom.meta.enums.role.MediaRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 媒体字段的上传和存储提示。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntMetaMedia {
    /**
     * 媒体字段角色。
     */
    MediaRole value() default MediaRole.UNSET;

    /**
     * 媒体路径存储模式。
     */
    PathMode pathMode() default PathMode.UNSET;

    /**
     * 可接收的媒体类型列表（MIME 或扩展名）。
     */
    String[] accept() default {};

    /**
     * 最大文件数量，-1 表示不限制。
     */
    int maxCount() default -1;

    /**
     * 单个文件最大大小（字节），-1 表示不限制。
     */
    long maxSize() default -1L;

    /**
     * 媒体路径表达方式。
     */
    enum PathMode {
        /** 未设置。 */
        UNSET,
        /** 对象存储键。 */
        OBJECT_KEY,
        /** 相对路径。 */
        RELATIVE_PATH,
        /** 绝对 URL。 */
        ABSOLUTE_URL
    }
}
