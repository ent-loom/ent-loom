package com.entloom.crud.starter.command.scene;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.RowsResult;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;

/**
 * Spring 环境下返回影响行数的实体 DELETE 场景模板。
 *
 * @param <T> 实体类型
 */
public abstract class CrudDeleteHandler<T>
    extends CrudTransactionalDeleteHandler<T, CommandResult<RowsResult>> {

    @Override
    protected final CommandResult<RowsResult> handleDelete(
        CommandSpec<DeleteTarget> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    ) {
        delete(spec.getPayload());
        return CommandResult.success(RowsResult.of(1));
    }

    protected abstract void delete(DeleteTarget target);
}
