package com.entloom.meta.contract.descriptor;

import java.util.List;

/**
 * 解析后的通用实体语义描述。
 */
public interface EntEntityDescriptor extends SourcedDescriptor {
    /**
     * 实体 Java 类型。
     */
    Class<?> entityClass();

    /**
     * 实体英文标识。
     */
    String entityName();

    /**
     * 所属服务名。
     */
    String serviceName();

    /**
     * 实体展示名称。
     */
    String label();

    /**
     * 实体说明文本。
     */
    String description();

    /**
     * 默认标签字段集合。
     */
    List<String> defaultLabelFields();

    /**
     * 预估数据量，null 表示未设置。
     */
    Long plannedVolume();

    /**
     * 字段描述集合。
     */
    List<? extends EntFieldDescriptor> fields();

    /**
     * 关系描述集合。
     */
    List<? extends EntRelationDescriptor> relations();

    /**
     * 索引描述集合。
     */
    List<? extends EntIndexDescriptor> indexes();
}
