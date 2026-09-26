package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * ACTION 场景的模板基类，将业务返回值包装为成功结果。
 */
public abstract class AbstractSimpleActionHandler<P, R> implements CommandActionSceneHandler<P, R> {
    @Override
    public final CommandResult<R> handle(
        CommandSpec<P> spec,
        SceneDelegate<CommandSpec<P>, CommandResult<R>> delegate
    ) {
        return CommandResult.success(execute(spec.getPayload()));
    }

    protected abstract R execute(P payload);
}
