package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * Command 场景处理器族接口。
 */
public interface CommandSceneHandler<P, R> extends SceneHandler<CommandSpec<P>, R> {
    /**
     * 当前 handler 处理的命令操作类型。
     */
    CommandOperation operation();
}
