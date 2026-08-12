package com.entloom.crud.api.enums;

/**
 * CRUD 框架操作域。
 */
public enum CrudOperationDomain {
    /** 普通只读查询。 */
    QUERY,
    /** 写入和业务动作。 */
    COMMAND,
    /** 聚合统计查询。 */
    STATS,
    /** 数据导入。 */
    IMPORT,
    /** 数据导出。 */
    EXPORT
}
