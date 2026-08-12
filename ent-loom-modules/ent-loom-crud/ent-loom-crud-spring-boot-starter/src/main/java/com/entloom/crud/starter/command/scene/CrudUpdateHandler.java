package com.entloom.crud.starter.command.scene;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.RowsResult;

/**
 * Spring 环境下返回影响行数的实体 UPDATE 场景模板。
 *
 * @param <T> 实体类型
 */
public abstract class CrudUpdateHandler<T>
    extends CrudTransactionalUpdateHandler<T, CommandResult<RowsResult>> {

    @Override
    protected final CommandResult<RowsResult> handleEntity(T requested) {
        update(requested);
        return CommandResult.success(RowsResult.of(1));
    }

    protected abstract void update(T requested);
}
