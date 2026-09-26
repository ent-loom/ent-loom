package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collections;
import java.util.Set;

/** ACTION 注册对象，将路由和类型契约绑定到业务处理器。 */
public final class CommandActionRegistration<P, R> implements CommandSceneHandler<P, CommandResult<R>> {
    private final Set<CrudRouteKey> routeKeys;
    private final CommandActionContract contract;
    private final CommandActionSceneHandler<P, R> handler;

    public CommandActionRegistration(Class<?> entityClass, String scene, Class<P> requestType,
        Class<R> responseType, CommandActionSceneHandler<P, R> handler) {
        if (entityClass == null || handler == null) {
            throw new ValidationException("ACTION 实体类型和处理器不能为空");
        }
        String normalizedScene = RouteKeyFactory.normalizeScene(scene);
        if (normalizedScene.isEmpty()) {
            throw new ValidationException("ACTION scene 不能为空");
        }
        this.routeKeys = Collections.singleton(new CrudRouteKey(
            Collections.singletonList(entityClass.getName()),
            CrudOperationKey.of(CommandOperation.ACTION), normalizedScene));
        this.contract = new CommandActionContract(requestType, responseType);
        this.handler = handler;
    }

    @Override
    public CommandOperation operation() {
        return CommandOperation.ACTION;
    }

    @Override
    public Set<CrudRouteKey> routeKeys() {
        return routeKeys;
    }

    public CommandActionContract contract() {
        return contract;
    }

    @Override
    public CommandResult<R> handle(CommandSpec<P> spec,
        SceneDelegate<CommandSpec<P>, CommandResult<R>> delegate) {
        return handler.handle(spec, delegate);
    }
}
