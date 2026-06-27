package com.entloom.crud.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级导出元数据声明。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntCrudExportField {
    /** 是否允许导出。 */
    boolean exportable() default true;

    /** 未显式传 fields 时是否默认展示。 */
    boolean defaultVisible() default true;

    /** 导出表头。 */
    String label() default "";

    /** 导出格式提示。 */
    String format() default "";

    /** 字典编码。 */
    String dictionaryCode() default "";

    /** 外键引用对应的同表展示字段。 */
    String displayField() default "";
}
