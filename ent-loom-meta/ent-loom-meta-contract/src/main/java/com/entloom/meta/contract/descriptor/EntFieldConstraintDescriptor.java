package com.entloom.meta.contract.descriptor;

/**
 * 通用字段约束描述。
 */
public interface EntFieldConstraintDescriptor extends SourcedDescriptor {
    /**
     * 约束名。
     */
    String name();

    /**
     * 约束值，使用字符串表达以避免引入子框架专属模型。
     */
    String value();
}
