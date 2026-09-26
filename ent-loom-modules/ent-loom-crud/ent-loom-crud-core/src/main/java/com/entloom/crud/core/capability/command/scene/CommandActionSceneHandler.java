package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.scene.SceneDelegate;

/**
 * Command ACTION 业务处理器，路由和类型契约由注册层提供。
 */
@FunctionalInterface
public interface CommandActionSceneHandler<P, R> {
    CommandResult<R> handle(CommandSpec<P> spec,
        SceneDelegate<CommandSpec<P>, CommandResult<R>> delegate);
}
