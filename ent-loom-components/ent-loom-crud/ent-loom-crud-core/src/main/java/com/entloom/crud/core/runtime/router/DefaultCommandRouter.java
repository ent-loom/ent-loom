package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.scene.CommandDispatcherAdapter;
import com.entloom.crud.core.capability.command.scene.CommandSceneHandler;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.List;

/**
 * Command 路由薄适配类：对外保留原类型名，内部走统一场景分发。
 */
public class DefaultCommandRouter implements CommandRouter, CommandActionSceneResolver {
    private final CommandDispatcherAdapter dispatcherAdapter;

    public DefaultCommandRouter(CommandEngine defaultCommandEngine) {
        this.dispatcherAdapter = new CommandDispatcherAdapter(defaultCommandEngine);
    }

    public void registerSceneHandler(CommandSceneHandler<?, ?> handler) {
        dispatcherAdapter.registerHandler(handler);
    }

    public void registerActionSceneHandler(CommandActionSceneHandler<?, ?> handler) {
        registerSceneHandler(handler);
    }

    public String canonicalizeActionScene(Class<?> rootType, List<Class<?>> entityClasses, String scene) {
        return dispatcherAdapter.canonicalizeActionScene(rootType, entityClasses, scene);
    }

    public CommandActionContract resolveActionContract(Class<?> rootType, List<Class<?>> entityClasses, String scene) {
        return dispatcherAdapter.resolveActionContract(rootType, entityClasses, scene);
    }

    @Override
    public <P, R> CommandRoute<P, R> route(CommandSpec<P> spec) {
        return dispatcherAdapter.route(spec);
    }
}
