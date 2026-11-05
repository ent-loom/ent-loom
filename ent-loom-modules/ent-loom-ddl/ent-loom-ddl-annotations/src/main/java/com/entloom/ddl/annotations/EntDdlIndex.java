package com.entloom.ddl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 实体数据库索引声明；字段使用 Java 属性名。 */
@Repeatable(EntDdlIndexes.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntDdlIndex {
    /** 索引名；为空时根据物理表名与有序列名自动生成，超长时附加稳定摘要。 */
    String name() default "";
    String[] fields() default {};
    boolean unique() default false;
    String expression() default "";
}
