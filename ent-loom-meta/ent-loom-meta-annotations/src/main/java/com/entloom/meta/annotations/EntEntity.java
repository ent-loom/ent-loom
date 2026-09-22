package com.entloom.meta.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个类可被属性系统识别为实体。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntEntity {

    /**
     * 实体展示名称，空字符串表示未显式指定。
     */
    String value() default "";

    /**
     * 实体英文标识，通常用于持久化和接口约定；为空时使用实体类简单名的小驼峰形式。
     */
    String entity() default "";

    /**
     * 实体说明文本。
     */
    String description() default "";

    /**
     * 所属服务名，跨服务引用时使用。
     */
    String service() default "";

    /** 对应数据库表名；为空时按实体类名推断。 */
    String table() default "";

    /**
     * 预估数据量，-1 表示未设置。
     */
    long plannedVolume() default -1L;
}
