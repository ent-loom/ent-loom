package com.entloom.crud.annotations;

import com.entloom.crud.api.enums.CrudIdPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CRUD 专属实体覆盖配置。
 *
 * <p>实体已声明 {@code @EntEntity} 时，该注解可以省略；通用 HTTP 仅发布显式声明此注解且未关闭的实体，并须绑定有效策略。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntCrudEntity {
    /** 共享业务策略码；为空表示由程序化策略绑定。 */
    String policy() default "";

    /** 是否发布通用 HTTP；默认发布，未声明 CRUD 的实体不会被注册。 */
    boolean httpExposed() default true;

    /** 实体最终开放的业务动作；为空且 actionsSpecified 为 false 时沿用共享策略动作。 */
    String[] actions() default {};

    /** 显式设置动作，用于声明空集合关闭所有动作。 */
    boolean actionsSpecified() default false;

    /** 在共享策略动作上追加的实体动作。 */
    String[] addActions() default {};

    /** 从共享策略动作中移除的实体动作。 */
    String[] removeActions() default {};

    /** 归属关系角色；空数组表示沿用全局关系推导。 */
    String[] ownership() default {};

    /** 是否显式设置 ownership；用于区分空集合关闭推导与省略继承。 */
    boolean ownershipSpecified() default false;

    /** 关系角色到实体字段，格式为 role=field。 */
    String[] relations() default {};

    /** 静态字段上限，格式为 USE:field1,field2；未声明用途关闭。 */
    String[] fieldLimits() default {};

    /** 创建时必须提供的字段。 */
    String[] requiredCreateFields() default {};

    /** 更新时禁止修改的字段。 */
    String[] forbiddenUpdateFields() default {};

    /** 通用 CRUD 操作覆盖，格式为 action=OP1,OP2；空值关闭映射。 */
    String[] operations() default {};
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
     * 主键写入策略；未配置时先根据主键字段上的持久化注解推断，
     * 再使用应用级默认值，最终按调用方显式提供主键处理。
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
     * 逻辑删除字段的未删除值，必须与字段 Java 类型兼容。
     *
     * @return 未删除值文本
     */
    String logicDeleteNotDeletedValue() default "";

    /**
     * 逻辑删除字段的已删除值，必须与字段 Java 类型兼容。
     *
     * @return 已删除值文本
     */
    String logicDeleteDeletedValue() default "";

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
