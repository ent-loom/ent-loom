package com.entloom.crud.annotations;

/** 声明式授权的基础范围；静态查看条件由 EntCrudGrant.where 声明。 */
public enum EntCrudGrantScope {
    /** 使用默认身份归属约定，允许实体覆盖字段。 */
    DEFAULT,
    /** 全量基础范围，仍受 where、强制治理与操作资格约束。 */
    ALL
}
