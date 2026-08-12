package com.entloom.crud.core.runtime.meta;

/**
 * 实体主键写入策略。
 */
public enum EntityIdPolicy {
    /** 外部显式传入主键，主键参与 insert。 */
    EXPLICIT,
    /** 数据库生成主键，create 不允许传入主键。 */
    GENERATED,
    /** 应用生成主键。当前闭环只声明策略，不提供默认生成器。 */
    APPLICATION,
    /** 联合主键。当前 JDBC 默认写入链不支持。 */
    COMPOSITE
}
