package com.entloom.crud.annotations;

import com.entloom.crud.api.enums.CrudIdPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务实体元数据注解。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudEntity {
    /**
     * 实体稳定名称。
     *
     * @return 名称
     */
    String name() default "";

    /**
     * 对应数据库表名。
     *
     * @return 表名
     */
    String table() default "";

    /**
     * 主键字段名。
     *
     * @return 主键字段
     */
    String idField() default "id";

    /**
     * 主键写入策略；未配置时由解析器根据主键字段上的持久化注解推断。
     *
     * @return 主键写入策略
     */
    CrudIdPolicy idPolicy() default CrudIdPolicy.UNSET;

    /**
     * 逻辑删除字段名。
     *
     * @return 逻辑删除字段
     */
    String logicDeleteField() default "";

    /**
     * 治理范围字段；创建时由可信治理范围提供，不能由普通载荷覆盖。
     *
     * @return 范围字段名
     */
    String[] scopeFields() default {};

    /**
     * 所属服务名。
     *
     * @return 服务名
     */
    String ownerService() default "";
}
