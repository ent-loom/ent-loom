package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collections;
import java.util.Set;

/**
 * 单实体 ACTION 场景的模板基类。
 * 统一封装 routeKey/contract/handle 样板逻辑，子类只关注业务执行。
 */
public abstract class AbstractSimpleActionHandler<P, R> implements CommandActionSceneHandler<P, R> {
    private final Set<CrudRouteKey> routeKeys;
    private final CommandActionContract contract;

    protected AbstractSimpleActionHandler(
        Class<?> entityClass,
        String scene,
        Class<P> requestType,
        Class<R> responseType
    ) {
        this.routeKeys = Collections.singleton(
            new CrudRouteKey(
                Collections.singletonList(entityClass.getName()),
                CrudOperationKey.of(CommandOperation.ACTION),
                RouteKeyFactory.normalizeScene(scene)
            )
        );
        this.contract = new CommandActionContract(requestType, responseType);
    }

    @Override
    public Set<CrudRouteKey> routeKeys() {
        return routeKeys;
    }

    @Override
    public CommandActionContract contract() {
        return contract;
    }

    @Override
    public final CommandResult<R> handle(
        CommandSpec<P> spec,
        SceneDelegate<CommandSpec<P>, CommandResult<R>> delegate
    ) {
        return CommandResult.success(execute(spec.getPayload()));
    }

    protected abstract R execute(P payload);
}
