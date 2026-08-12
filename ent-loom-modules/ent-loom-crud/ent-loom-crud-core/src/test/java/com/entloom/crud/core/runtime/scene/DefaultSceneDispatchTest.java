package com.entloom.crud.core.runtime.scene;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.exception.RouteAmbiguousException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultSceneDispatchTest {
    @Test
    void should_reject_duplicate_route_registration() {
        DefaultSceneHandlerRegistry<String, String> registry = new DefaultSceneHandlerRegistry<String, String>();
        CrudRouteKey routeKey = routeKey("page");

        registry.register(handler(routeKey, (spec, delegate) -> "first"));

        Assertions.assertThrows(RouteAmbiguousException.class, () ->
            registry.register(handler(routeKey, (spec, delegate) -> "second"))
        );
    }

    @Test
    void should_reject_empty_route_keys() {
        DefaultSceneHandlerRegistry<String, String> registry = new DefaultSceneHandlerRegistry<String, String>();

        Assertions.assertThrows(ValidationException.class, () ->
            registry.register(handler(Collections.<CrudRouteKey>emptySet(), (spec, delegate) -> spec))
        );
    }

    @Test
    void dispatcher_should_fallback_delegate_when_route_missing() {
        DefaultSceneHandlerRegistry<String, String> registry = new DefaultSceneHandlerRegistry<String, String>();
        UnifiedSceneDispatcher<String, String> dispatcher = new UnifiedSceneDispatcher<String, String>(registry);
        AtomicInteger delegateCalls = new AtomicInteger();

        String result = dispatcher.dispatch(routeKey("missing"), "input", spec -> {
            delegateCalls.incrementAndGet();
            return "default-" + spec;
        });

        Assertions.assertEquals("default-input", result);
        Assertions.assertEquals(1, delegateCalls.get());
    }

    @Test
    void dispatcher_should_allow_handler_to_wrap_delegate_result() {
        DefaultSceneHandlerRegistry<String, String> registry = new DefaultSceneHandlerRegistry<String, String>();
        CrudRouteKey routeKey = routeKey("page");
        registry.register(handler(routeKey, (spec, delegate) -> "custom-" + delegate.invoke(spec)));
        UnifiedSceneDispatcher<String, String> dispatcher = new UnifiedSceneDispatcher<String, String>(registry);

        String result = dispatcher.dispatch(routeKey, "input", spec -> "default-" + spec);

        Assertions.assertEquals("custom-default-input", result);
    }

    private CrudRouteKey routeKey(String scene) {
        return new CrudRouteKey(Collections.singletonList(Object.class.getName()), CrudOperationKey.of(QueryOperation.PAGE), scene);
    }

    private SceneHandler<String, String> handler(CrudRouteKey routeKey, HandlerLogic logic) {
        return handler(Collections.singleton(routeKey), logic);
    }

    private SceneHandler<String, String> handler(Set<CrudRouteKey> routeKeys, HandlerLogic logic) {
        return new SceneHandler<String, String>() {
            @Override
            public Set<CrudRouteKey> routeKeys() {
                return routeKeys;
            }

            @Override
            public String handle(String spec, SceneDelegate<String, String> delegate) {
                return logic.handle(spec, delegate);
            }
        };
    }

    private interface HandlerLogic {
        String handle(String spec, SceneDelegate<String, String> delegate);
    }
}
