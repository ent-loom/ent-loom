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
    /** 命名 YAML 授权基线或 Java 策略码；与实体上的 EntCrudGrant 互斥。 */
    String policy() default "";

    /** 是否发布通用 HTTP；默认发布，未声明 CRUD 的实体不会被注册。 */
    boolean httpExposed() default true;

    /** 不发布通用 HTTP /action/{scene} 的命令业务动作；使用规范 scene 码，不影响内部 Gateway 调用。 */
    String[] httpExcludedActions() default {};

    /** 框架内部的未指定标记，用于区分缺省与显式空动作集。 */
    String INHERIT_ACTIONS = "<default>";

    /** 完整替换项目默认动作集；空数组关闭全部动作，不可与 addActions/removeActions 混用。 */
    String[] actions() default {INHERIT_ACTIONS};

    /** 在项目默认动作集上追加操作。 */
    String[] addActions() default {};

    /** 从项目默认动作集中移除操作。 */
    String[] removeActions() default {};

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
     * 治理范围字段；缺省取全局身份归属字段与实体持久化标量字段的交集。
     * 显式数组完整替换（空数组关闭缺省保护）；创建时由可信范围补齐或校验。
     *
     * @return 范围字段名
     */
    String[] scopeFields() default {INHERIT_SCOPE_FIELDS};

    /** 框架内部未指定标记，区分缺省推导与显式空数组。 */
    String INHERIT_SCOPE_FIELDS = "<default>";

    /**
     * 所属服务名。
     *
     * @return 服务名
     */
    String ownerService() default "";
}
