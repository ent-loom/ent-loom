package com.entloom.ddl.api;

/**
 * DDL 执行级别。
 */
public enum DdlExecutionMode {
    /** 不生成、不执行任何 DDL。 */
    NONE,
    /** E1：只创建不存在的表。 */
    CREATE_TABLE,
    /** E1：创建不存在的表，并为后续 Meta 扩展保留模式边界。 */
    CREATE_TABLE_AND_METAS,
    /** 后续 E3：允许表结构修改和 Meta 处理。 */
    CREATE_MODIFY_TABLE_AND_METAS,
    /** 后续阶段：允许删除全部差异。 */
    CREATE_MODIFY_DELETE_ALL
}
