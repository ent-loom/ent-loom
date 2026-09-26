package com.entloom.crud.starter;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.annotations.EntCrudActions;
import com.entloom.crud.annotations.EntCrudAction;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.exception.RouteAmbiguousException;
import com.entloom.crud.core.runtime.router.DefaultCommandRouter;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.starter.config.module.SceneHandlerRegistrar;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandActionRegistrationTest {
    @Test
    void should_resolve_inherited_generics_and_execute_through_spring_proxy() {
        for (boolean classProxy : new boolean[] {false, true}) {
            AtomicInteger invocations = new AtomicInteger();
            ProxyFactory factory = new ProxyFactory(new BoundAction());
            factory.setProxyTargetClass(classProxy);
            factory.addAdvice((MethodInterceptor) invocation -> {
                invocations.incrementAndGet();
                return invocation.proceed();
            });
            CommandActionSceneHandler<?, ?> proxy = (CommandActionSceneHandler<?, ?>) factory.getProxy();
            try (GenericApplicationContext context = context(proxy)) {
                DefaultCommandRouter router = register(context);
                CommandActionContract contract = router.resolveActionContract(
                    TestEntity.class, Collections.<Class<?>>singletonList(TestEntity.class), " ORDER.PLACE ");
                assertThat(contract.getRequestType()).isEqualTo(String.class);
                assertThat(contract.getResponseType()).isEqualTo(String.class);

                CommandSpec<String> spec = CommandSpec.<String>builder()
                    .rootType(TestEntity.class)
                    .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
                    .op(CommandOperation.ACTION)
                    .scene("order.place")
                    .payload("ORD-1")
                    .build();
                CommandResult<?> result = (CommandResult<?>) router.route(spec).handler().action(spec);
                assertThat(result.getData()).isEqualTo("ORD-1");
                assertThat(invocations.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void should_reject_unresolved_generics() {
        try (GenericApplicationContext context = context(new GenericAction<String, String>())) {
            assertThatThrownBy(() -> register(context)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("明确的请求与响应泛型");
        }
    }

    @Test
    void should_reject_missing_route_annotation() {
        try (GenericApplicationContext context = context(new UnannotatedAction())) {
            assertThatThrownBy(() -> register(context)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("必须声明 @EntCrudCommandAction");
        }
    }

    @Test
    void should_reject_empty_scene() {
        try (GenericApplicationContext context = context(new EmptySceneAction())) {
            assertThatThrownBy(() -> register(context)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("scene 不能为空");
        }
    }

    @Test
    void should_reject_duplicate_routes() {
        try (GenericApplicationContext context = context(new BoundAction())) {
            context.getBeanFactory().registerSingleton("duplicateAction", new BoundAction());
            assertThatThrownBy(() -> register(context)).isInstanceOf(RouteAmbiguousException.class)
                .hasMessageContaining("路由重复注册");
        }
    }

    @Test
    void should_reject_handler_outside_entity_action_boundary() {
        try (GenericApplicationContext context = context(new ForbiddenAction())) {
            assertThatThrownBy(() -> register(context)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("超出实体动作边界");
        }
    }

    private GenericApplicationContext context(CommandActionSceneHandler<?, ?> handler) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("action", handler);
        context.refresh();
        return context;
    }

    private DefaultCommandRouter register(GenericApplicationContext context) {
        DefaultCommandRouter router = new DefaultCommandRouter(null);
        new SceneHandlerRegistrar(context, null, router, null, null, null, null).afterPropertiesSet();
        return router;
    }

    @EntCrudCommandAction(entityClass = TestEntity.class, scene = "ORDER.PLACE")
    public static class GenericAction<P, R> implements CommandActionSceneHandler<P, R> {
        @Override
        @SuppressWarnings("unchecked")
        public CommandResult<R> handle(CommandSpec<P> spec,
            SceneDelegate<CommandSpec<P>, CommandResult<R>> delegate) {
            return CommandResult.success((R) spec.getPayload());
        }
    }

    public static class BoundAction extends GenericAction<String, String> {
    }

    @EntCrudCommandAction(entityClass = TestEntity.class, scene = " ")
    public static class EmptySceneAction extends BoundAction {
    }

    @EntCrudCommandAction(entityClass = RestrictedEntity.class, scene = "cancel")
    public static class ForbiddenAction extends BoundAction {
    }

    @EntCrudActions(@EntCrudAction(value = "place", name = "下单", capability = "place-order"))
    private static class RestrictedEntity {
    }

    public static class UnannotatedAction implements CommandActionSceneHandler<String, String> {
        @Override
        public CommandResult<String> handle(CommandSpec<String> spec,
            SceneDelegate<CommandSpec<String>, CommandResult<String>> delegate) {
            return CommandResult.success(spec.getPayload());
        }
    }

    private static class TestEntity {
    }
}
