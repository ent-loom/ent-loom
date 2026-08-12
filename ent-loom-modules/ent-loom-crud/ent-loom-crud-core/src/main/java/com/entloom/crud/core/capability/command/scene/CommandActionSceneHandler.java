package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;

/**
 * Command ACTION 场景处理器。
 */
public interface CommandActionSceneHandler<P, R> extends CommandSceneHandler<P, CommandResult<R>> {
    @Override
    default CommandOperation operation() {
        return CommandOperation.ACTION;
    }

    /**
     * ACTION 入参与出参契约。
     */
    CommandActionContract contract();
}
