package com.entloom.meta.contract.descriptor;

import com.entloom.base.util.value.TypedValueType;
import java.util.List;

/**
 * 解析后的通用字段语义描述。
 */
public interface EntFieldDescriptor extends SourcedDescriptor {
    /**
     * Java 字段名。
     */
    String fieldName();

    /**
     * Java 字段类型。
     */
    Class<?> javaType();

    /**
     * 字段语义类型名。
     */
    String fieldKind();

    /**
     * 字段业务角色，null 表示未设置。
     */
    String role();

    /**
     * 字段展示名称。
     */
    String label();

    /**
     * 字段说明文本。
     */
    String description();

    /**
     * 示例值列表。
     */
    List<String> examples();

    /**
     * 创建时业务默认值原始文本，null 表示未设置。
     */
    String createDefaultValue();

    /**
     * 创建时业务默认值类型。
     */
    TypedValueType createDefaultValueType();

    /**
     * 创建时业务默认值的类型化结果，null 表示未设置。
     */
    Object typedCreateDefaultValue();

    /**
     * 通用基础约束集合。
     */
    List<? extends EntFieldConstraintDescriptor> constraints();

    /**
     * 是否显式必填，null 表示未设置。
     */
    Boolean required();

    /**
     * 是否显式只读，null 表示未设置。
     */
    Boolean readOnly();
}
