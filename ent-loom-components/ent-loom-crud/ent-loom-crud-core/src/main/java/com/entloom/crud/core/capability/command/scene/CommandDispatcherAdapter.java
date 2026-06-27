package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.command.handler.CommandHandler;
import com.entloom.crud.core.runtime.router.CommandRoute;
import com.entloom.crud.core.runtime.router.CommandRouter;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.router.DefaultCommandRoute;
import com.entloom.crud.core.runtime.scene.DefaultSceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.UnifiedSceneDispatcher;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command 分域分发适配器。
 */
public class CommandDispatcherAdapter implements CommandRouter {
    private final CommandEngine defaultCommandEngine;
    private final Map<CommandOperation, WriteSceneRuntime> writeRuntimes;
    private final SceneHandlerRegistry<CommandSpec<Object>, CommandResult<Object>> actionRegistry;
    private final UnifiedSceneDispatcher<CommandSpec<Object>, CommandResult<Object>> actionDispatcher;

    public CommandDispatcherAdapter(CommandEngine defaultCommandEngine) {
        this(
            defaultCommandEngine,
            new DefaultSceneHandlerRegistry<CommandSpec<Object>, Object>(),
            new DefaultSceneHandlerRegistry<CommandSpec<Object>, Object>(),
            new DefaultSceneHandlerRegistry<CommandSpec<Object>, Object>(),
            new DefaultSceneHandlerRegistry<CommandSpec<Object>, CommandResult<Object>>()
        );
    }

    public CommandDispatcherAdapter(
        CommandEngine defaultCommandEngine,
        SceneHandlerRegistry<CommandSpec<Object>, Object> createRegistry,
        SceneHandlerRegistry<CommandSpec<Object>, Object> updateRegistry,
        SceneHandlerRegistry<CommandSpec<Object>, Object> deleteRegistry,
        SceneHandlerRegistry<CommandSpec<Object>, CommandResult<Object>> actionRegistry
    ) {
        this.defaultCommandEngine = defaultCommandEngine;
        this.writeRuntimes = new EnumMap<CommandOperation, WriteSceneRuntime>(CommandOperation.class);
        this.writeRuntimes.put(CommandOperation.CREATE, new WriteSceneRuntime(createRegistry));
        this.writeRuntimes.put(CommandOperation.UPDATE, new WriteSceneRuntime(updateRegistry));
        this.writeRuntimes.put(CommandOperation.DELETE, new WriteSceneRuntime(deleteRegistry));
        this.actionRegistry = actionRegistry;
        this.actionDispatcher = new UnifiedSceneDispatcher<CommandSpec<Object>, CommandResult<Object>>(actionRegistry);
    }

    public void registerHandler(CommandSceneHandler<?, ?> handler) {
        if (handler == null) {
            throw new ValidationException("handler 不能为空");
        }
        CommandOperation operation = handler.operation();
        if (operation == null) {
            throw new ValidationException("Command handler operation 不能为空: " + handler.getClass().getName());
        }
        validateHandlerRouteKeys(handler, operation);
        switch (operation) {
            case CREATE:
            case UPDATE:
            case DELETE:
                writeRuntime(operation).registry.register(castCommandHandler(handler));
                break;
            case ACTION:
                if (!(handler instanceof CommandActionSceneHandler)) {
                    throw new ValidationException("ACTION handler 必须实现 CommandActionSceneHandler: " + handler.getClass().getName());
                }
                actionRegistry.register(castActionHandler((CommandActionSceneHandler<?, ?>) handler));
                break;
            default:
                throw new ValidationException("不支持注册 Command scene handler op: " + operation);
        }
    }

    private void validateHandlerRouteKeys(CommandSceneHandler<?, ?> handler, CommandOperation operation) {
        Set<CrudRouteKey> routeKeys = handler.routeKeys();
        if (routeKeys == null || routeKeys.isEmpty()) {
            throw new ValidationException("routeKeys 不能为空: " + handler.getClass().getName());
        }
        for (CrudRouteKey routeKey : routeKeys) {
            if (routeKey == null) {
                throw new ValidationException("routeKey 不能为空: " + handler.getClass().getName());
            }
            if (!CrudOperationKey.of(operation).equals(routeKey.getOperationKey())) {
                throw new ValidationException(
                    "Command handler op 不匹配: expected=" + CrudOperationKey.of(operation)
                        + ", actual=" + routeKey.getOperationKey()
                        + ", handler=" + handler.getClass().getName()
                );
            }
        }
    }

    public void registerActionHandler(CommandActionSceneHandler<?, ?> handler) {
        registerHandler(handler);
    }

    public String canonicalizeActionScene(Class<?> rootType, List<Class<?>> entityClasses, String scene) {
        CrudRouteKey routeKey = buildActionRouteKey(rootType, entityClasses, scene);
        resolveActionHandler(routeKey);
        return routeKey.getScene();
    }

    public CommandActionContract resolveActionContract(Class<?> rootType, List<Class<?>> entityClasses, String scene) {
        CrudRouteKey routeKey = buildActionRouteKey(rootType, entityClasses, scene);
        return resolveActionHandler(routeKey).contract();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P, R> CommandRoute<P, R> route(final CommandSpec<P> spec) {
        if (spec == null) {
            throw new ValidationException("spec 不能为空");
        }
        if (spec.getOp() == null) {
            throw new ValidationException("Command spec.op 不能为空");
        }
        final CrudRouteKey routeKey = RouteKeyFactory.buildCommandRoute(spec);
        switch (spec.getOp()) {
            case CREATE:
            case UPDATE:
            case DELETE:
                return resolveWriteRoute(routeKey, spec.getOp());
            case SAVE_OR_UPDATE:
            case CREATE_BATCH:
            case UPDATE_BATCH:
            case DELETE_BATCH:
            case SAVE_OR_UPDATE_BATCH:
                return defaultRoute();
            case ACTION:
                return resolveActionRoute(spec, routeKey);
            default:
                throw new ValidationException("不支持的 Command op: " + spec.getOp());
        }
    }

    @SuppressWarnings("unchecked")
    private <P, R> CommandRoute<P, R> resolveWriteRoute(final CrudRouteKey routeKey, CommandOperation operation) {
        final WriteSceneRuntime runtime = writeRuntime(operation);
        final SceneHandler<CommandSpec<Object>, Object> sceneHandler = runtime.registry.resolveOrNull(routeKey);
        if (routeKey.getScene().isEmpty() && sceneHandler == null) {
            return defaultRoute();
        }
        if (sceneHandler == null) {
            throw new RouteNotFoundException("未找到命令路由: " + routeKey);
        }
        CommandHandler<P, R> handler = new CommandHandler<P, R>() {
            @Override
            public boolean supports(CommandSpec<P> commandSpec) {
                return true;
            }

            @Override
            public R action(CommandSpec<P> commandSpec) {
                Object result = runtime.dispatcher.dispatch(
                    routeKey,
                    (CommandSpec<Object>) commandSpec,
                    new SceneDelegate<CommandSpec<Object>, Object>() {
                        @Override
                        public Object invoke(CommandSpec<Object> delegateSpec) {
                            return defaultCommandEngine.action(delegateSpec);
                        }
                    }
                );
                return (R) result;
            }
        };
        return new DefaultCommandRoute<P, R>(handler);
    }

    @SuppressWarnings("unchecked")
    private <P, R> CommandRoute<P, R> resolveActionRoute(final CommandSpec<P> spec, final CrudRouteKey routeKey) {
        if (routeKey.getScene().isEmpty()) {
            throw new RouteNotFoundException("执行操作需要自定义命令处理器");
        }
        final SceneHandler<CommandSpec<Object>, CommandResult<Object>> sceneHandler = actionRegistry.resolveOrNull(routeKey);
        if (sceneHandler == null) {
            throw new RouteNotFoundException("未找到命令路由: " + routeKey);
        }
        CommandHandler<P, R> handler = new CommandHandler<P, R>() {
            @Override
            public boolean supports(CommandSpec<P> commandSpec) {
                return true;
            }

            @Override
            public R action(CommandSpec<P> commandSpec) {
                CommandResult<Object> result = actionDispatcher.dispatch(
                    routeKey,
                    (CommandSpec<Object>) commandSpec,
                    new SceneDelegate<CommandSpec<Object>, CommandResult<Object>>() {
                        @Override
                        public CommandResult<Object> invoke(CommandSpec<Object> delegateSpec) {
                            throw new RouteNotFoundException("执行操作需要自定义命令处理器");
                        }
                    }
                );
                return (R) result;
            }
        };
        return new DefaultCommandRoute<P, R>(handler);
    }

    private <P, R> CommandRoute<P, R> defaultRoute() {
        if (defaultCommandEngine == null) {
            throw new RouteNotFoundException("未找到默认命令执行器");
        }
        CommandHandler<P, R> handler = new CommandHandler<P, R>() {
            @Override
            public boolean supports(CommandSpec<P> commandSpec) {
                return true;
            }

            @Override
            public R action(CommandSpec<P> commandSpec) {
                return defaultCommandEngine.action(commandSpec);
            }
        };
        return new DefaultCommandRoute<P, R>(handler);
    }

    @SuppressWarnings("unchecked")
    private SceneHandler<CommandSpec<Object>, Object> castCommandHandler(CommandSceneHandler<?, ?> handler) {
        return (SceneHandler<CommandSpec<Object>, Object>) (SceneHandler<?, ?>) handler;
    }

    @SuppressWarnings("unchecked")
    private SceneHandler<CommandSpec<Object>, CommandResult<Object>> castActionHandler(CommandActionSceneHandler<?, ?> handler) {
        return (SceneHandler<CommandSpec<Object>, CommandResult<Object>>) (SceneHandler<?, ?>) handler;
    }

    @SuppressWarnings("unchecked")
    private CommandActionSceneHandler<Object, Object> resolveActionHandler(CrudRouteKey routeKey) {
        SceneHandler<CommandSpec<Object>, CommandResult<Object>> handler = actionRegistry.resolveOrNull(routeKey);
        if (handler == null) {
            throw new RouteNotFoundException("未找到命令路由: " + routeKey);
        }
        if (!(handler instanceof CommandActionSceneHandler)) {
            throw new ValidationException("ACTION 路由处理器类型非法: " + handler.getClass().getName());
        }
        return (CommandActionSceneHandler<Object, Object>) (Object) handler;
    }

    private CrudRouteKey buildActionRouteKey(Class<?> rootType, List<Class<?>> entityClasses, String scene) {
        CommandSpec<Object> probe = CommandSpec.<Object>builder()
            .rootType(rootType)
            .entityClasses(entityClasses)
            .scene(scene)
            .op(CommandOperation.ACTION)
            .build();
        return RouteKeyFactory.buildCommandRoute(probe);
    }

    private WriteSceneRuntime writeRuntime(CommandOperation operation) {
        WriteSceneRuntime runtime = writeRuntimes.get(operation);
        if (runtime == null) {
            throw new ValidationException("不支持注册 Command scene handler op: " + operation);
        }
        return runtime;
    }

    private static final class WriteSceneRuntime {
        private final SceneHandlerRegistry<CommandSpec<Object>, Object> registry;
        private final UnifiedSceneDispatcher<CommandSpec<Object>, Object> dispatcher;

        private WriteSceneRuntime(SceneHandlerRegistry<CommandSpec<Object>, Object> registry) {
            this.registry = registry;
            this.dispatcher = new UnifiedSceneDispatcher<CommandSpec<Object>, Object>(registry);
        }
    }

}
