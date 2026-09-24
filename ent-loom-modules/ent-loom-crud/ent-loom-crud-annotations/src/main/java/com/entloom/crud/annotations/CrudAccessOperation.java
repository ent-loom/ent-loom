package com.entloom.crud.annotations;

/**
 * 实体允许的标准 CRUD 操作。
 */
public enum CrudAccessOperation {
    /** 列表查询。 */
    LIST,
    /** 分页查询。 */
    PAGE,
    /** 可空唯一查询。 */
    FIND_ONE,
    /** 详情查询。 */
    DETAIL,
    /** 新增。 */
    CREATE,
    /** 更新。 */
    UPDATE,
    /** 删除。 */
    DELETE
}
