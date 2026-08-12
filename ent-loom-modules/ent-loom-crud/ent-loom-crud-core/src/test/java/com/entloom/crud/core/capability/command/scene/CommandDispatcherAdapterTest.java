package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.engine.CommandEngine;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommandDispatcherAdapterTest {
    @Test
    void should_fallback_default_engine_for_create_when_scene_empty() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return (R) "default-create";
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(CommandOperation.CREATE)
            .build();
        String result = (String) adapter.route(spec).handler().action(spec);
        Assertions.assertEquals("default-create", result);
    }

    @Test
    void should_dispatch_create_handler_when_scene_empty_and_route_registered() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return (R) "default-create";
            }
        });
        final CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(CommandOperation.CREATE),
            null
        );
        adapter.registerHandler(new CommandSceneHandler<Object, Object>() {
            @Override
            public CommandOperation operation() {
                return CommandOperation.CREATE;
            }

            @Override
            public Set<CrudRouteKey> routeKeys() {
                return Collections.singleton(routeKey);
            }

            @Override
            public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
                return "custom-create";
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(CommandOperation.CREATE)
            .build();
        String result = (String) adapter.route(spec).handler().action(spec);
        Assertions.assertEquals("custom-create", result);
    }

    @Test
    void should_register_command_scene_handler_by_operation() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return (R) "default-update";
            }
        });
        final CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(CommandOperation.UPDATE),
            "unified"
        );
        adapter.registerHandler(new CommandSceneHandler<Object, Object>() {
            @Override
            public CommandOperation operation() {
                return CommandOperation.UPDATE;
            }

            @Override
            public Set<CrudRouteKey> routeKeys() {
                return Collections.singleton(routeKey);
            }

            @Override
            public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
                return "custom-unified-update";
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .scene("unified")
            .op(CommandOperation.UPDATE)
            .build();
        String result = (String) adapter.route(spec).handler().action(spec);
        Assertions.assertEquals("custom-unified-update", result);
    }

    @Test
    void should_fail_when_handler_operation_and_route_key_operation_mismatch() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return null;
            }
        });
        final CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(CommandOperation.CREATE),
            "mismatch"
        );
        ValidationException ex = Assertions.assertThrows(ValidationException.class, () ->
            adapter.registerHandler(new CommandSceneHandler<Object, Object>() {
                @Override
                public CommandOperation operation() {
                    return CommandOperation.UPDATE;
                }

                @Override
                public Set<CrudRouteKey> routeKeys() {
                    return Collections.singleton(routeKey);
                }

                @Override
                public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
                    return null;
                }
            })
        );
        Assertions.assertTrue(ex.getMessage().contains("Command handler op 不匹配"));
    }

    @Test
    void should_dispatch_update_handler_when_scene_empty_and_route_registered() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return (R) "default-update";
            }
        });
        final CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(CommandOperation.UPDATE),
            null
        );
        adapter.registerHandler(new CommandSceneHandler<Object, Object>() {
            @Override
            public CommandOperation operation() {
                return CommandOperation.UPDATE;
            }

            @Override
            public Set<CrudRouteKey> routeKeys() {
                return Collections.singleton(routeKey);
            }

            @Override
            public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
                return "custom-update";
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(CommandOperation.UPDATE)
            .build();
        String result = (String) adapter.route(spec).handler().action(spec);
        Assertions.assertEquals("custom-update", result);
    }

    @Test
    void should_dispatch_delete_handler_when_scene_empty_and_route_registered() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return (R) "default-delete";
            }
        });
        final CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(CommandOperation.DELETE),
            null
        );
        adapter.registerHandler(new CommandSceneHandler<Object, Object>() {
            @Override
            public CommandOperation operation() {
                return CommandOperation.DELETE;
            }

            @Override
            public Set<CrudRouteKey> routeKeys() {
                return Collections.singleton(routeKey);
            }

            @Override
            public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
                return "custom-delete";
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(CommandOperation.DELETE)
            .build();
        String result = (String) adapter.route(spec).handler().action(spec);
        Assertions.assertEquals("custom-delete", result);
    }

    @Test
    void should_fail_for_create_scene_miss() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return null;
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .scene("create.special")
            .op(CommandOperation.CREATE)
            .build();
        Assertions.assertThrows(RouteNotFoundException.class, () -> adapter.route(spec));
    }

    @Test
    void should_dispatch_action_and_resolve_contract() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return null;
            }
        });
        adapter.registerActionHandler(new PlaceActionHandler());

        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .scene("ORDER.PLACE")
            .op(CommandOperation.ACTION)
            .payload("ORD-1")
            .build();

        CommandResult<?> result = (CommandResult<?>) adapter.route(spec).handler().action(spec);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("ORD-1", result.getData());
        Assertions.assertEquals(
            "order.place",
            adapter.canonicalizeActionScene(TestEntity.class, Collections.<Class<?>>singletonList(TestEntity.class), " ORDER.PLACE ")
        );
        CommandActionContract contract = adapter.resolveActionContract(
            TestEntity.class,
            Collections.<Class<?>>singletonList(TestEntity.class),
            "order.place"
        );
        Assertions.assertEquals(String.class, contract.getRequestType());
        Assertions.assertEquals(String.class, contract.getResponseType());
    }

    @Test
    void should_fail_action_without_scene() {
        CommandDispatcherAdapter adapter = new CommandDispatcherAdapter(new CommandEngine() {
            @Override
            public <P, R> R action(CommandSpec<P> spec) {
                return null;
            }
        });
        CommandSpec<Object> spec = CommandSpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(CommandOperation.ACTION)
            .build();
        Assertions.assertThrows(RouteNotFoundException.class, () -> adapter.route(spec));
    }

    private static final class PlaceActionHandler implements CommandActionSceneHandler<Object, String> {
        @Override
        public Set<CrudRouteKey> routeKeys() {
            return Collections.singleton(
                new CrudRouteKey(
                    Collections.singletonList(TestEntity.class.getName()),
                    CrudOperationKey.of(CommandOperation.ACTION),
                    RouteKeyFactory.normalizeScene("order.place")
                )
            );
        }

        @Override
        public CommandActionContract contract() {
            return new CommandActionContract(String.class, String.class);
        }

        @Override
        public CommandResult<String> handle(
            CommandSpec<Object> spec,
            SceneDelegate<CommandSpec<Object>, CommandResult<String>> delegate
        ) {
            return CommandResult.success(spec.getPayload() == null ? null : String.valueOf(spec.getPayload()));
        }
    }

    private static final class TestEntity {
    }
}
