package com.entloom.crud.starter.command.scene;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.CreateResult;

/**
 * Spring 环境下返回创建结果的实体 CREATE 场景模板。
 *
 * @param <T> 实体类型
 */
public abstract class CrudCreateHandler<T>
    extends CrudTransactionalCreateHandler<T, CommandResult<CreateResult>> {

    @Override
    protected final CommandResult<CreateResult> handleEntity(T requested) {
        return CommandResult.success(create(requested));
    }

    protected abstract CreateResult create(T requested);
}
