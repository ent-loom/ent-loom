package com.entloom.crud.annotations;

/** 声明式授权范围，不支持脚本或任意 SQL。 */
public enum EntCrudGrantScope {
    /** 使用全局身份归属约定，允许实体覆盖字段。 */
    DEFAULT,
    /** 全量数据，仍受身份和操作资格约束。 */
    ALL,
    /** 固定字段等值条件。 */
    EQUAL
}
