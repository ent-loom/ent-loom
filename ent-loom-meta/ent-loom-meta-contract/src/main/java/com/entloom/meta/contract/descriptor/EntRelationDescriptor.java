package com.entloom.meta.contract.descriptor;

import com.entloom.meta.enums.RelationCardinality;
import com.entloom.meta.contract.enums.RelationOwnerSide;
import com.entloom.meta.contract.enums.RelationResolutionStatus;

/**
 * 解析后的通用关系语义描述。
 */
public interface EntRelationDescriptor extends SourcedDescriptor {
    /** 承载关系的 Java 属性名，与参与关联的 sourceField 独立。 */
    default String relationField() {
        com.entloom.meta.contract.value.SourcedValue<?> value = sourcedValue(MetaDescriptorProperties.FIELD_NAME);
        return value == null ? sourceField() : (String) value.value();
    }

    /**
     * 当前实体上的来源字段名。
     */
    String sourceField();

    /**
     * 目标实体所属服务名。
     */
    String targetService();

    /**
     * 目标实体标识。
     */
    String targetEntity();

    /**
     * 目标实体字段名。
     */
    String targetField();

    /**
     * 关系基数。
     */
    RelationCardinality cardinality();

    /**
     * 关系执行数据的拥有侧。
     */
    RelationOwnerSide ownerSide();

    /**
     * 关系解析完整度。
     */
    RelationResolutionStatus resolutionStatus();

    /**
     * sourceField 是否由被注解字段名推断。
     */
    boolean sourceFieldInferred();
}
