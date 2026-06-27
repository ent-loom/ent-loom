package com.entloom.ddl.annotations;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.base.util.value.TypedValueType;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.ddl.enums.SqlType;
import com.entloom.ddl.enums.WritePolicy;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段的数据库列映射定义。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EntDbField {
    /**
     * 物理列名。空值表示按命名策略推导。
     */
    String column() default "";

    /**
     * SQL 类型。AUTO 表示按 Java 类型和上层语义自动推导。
     */
    SqlType sqlType() default SqlType.AUTO;

    /**
     * 列定义（例如 varchar(64), decimal(18,2)）。AUTO 表示自动推导。
     */
    String columnDefinition() default "";

    /**
     * 主长度（例如 varchar length、decimal precision）。
     */
    int length() default -1;

    /**
     * 精度（通常与 decimal precision 语义一致）。
     */
    int precision() default -1;

    /**
     * 小数位长度（例如 decimal scale）。
     */
    int scale() default -1;

    /**
     * 列排序规则。空字符串表示使用数据库或表默认排序规则。
     */
    String collation() default "";

    /**
     * 数据库方言附加列属性。空字符串表示未显式指定。
     */
    String dialectOptions() default "";

    /**
     * 是否参与数据库列映射。UNSET 表示默认参与。
     */
    OptionalBoolean persisted() default OptionalBoolean.UNSET;

    /**
     * 是否允许为 null。UNSET 表示按类型和策略推导。
     */
    OptionalBoolean nullable() default OptionalBoolean.UNSET;

    /**
     * 是否唯一约束。UNSET 表示按索引和策略推导。
     */
    OptionalBoolean unique() default OptionalBoolean.UNSET;

    /**
     * 是否主键。UNSET 表示由策略推导。
     */
    OptionalBoolean primaryKey() default OptionalBoolean.UNSET;

    /**
     * 值生成策略。UNSET 表示未显式指定。
     */
    GenerationStrategy generationStrategy() default GenerationStrategy.UNSET;

    /**
     * 列注释。
     */
    String comment() default "";

    /**
     * 默认值，空字符串表示未显式指定。
     */
    String defaultValue() default "";

    /**
     * 旧字段名，用于生成重命名迁移语句。
     */
    String renameFrom() default "";

    /**
     * 默认值解析提示。UNSET 表示按 sqlType 和方言推导。
     */
    TypedValueType defaultValueHint() default TypedValueType.UNSET;

    /**
     * 写入策略。
     */
    WritePolicy writePolicy() default WritePolicy.READ_WRITE;

}
