package com.entloom.crud.api.model;

/** 类型化单实体写入结果；创建返回主键，更新和删除仅返回影响行数。 */
public final class EntityWriteResult<ID> {
    private final int rows;
    private final ID id;

    public EntityWriteResult(int rows, ID id) {
        this.rows = rows;
        this.id = id;
    }

    public int getRows() {
        return rows;
    }

    public ID getId() {
        return id;
    }
}
