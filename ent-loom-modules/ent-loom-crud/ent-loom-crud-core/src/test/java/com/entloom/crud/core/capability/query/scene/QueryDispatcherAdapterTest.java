package com.entloom.crud.core.capability.query.scene;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class QueryDispatcherAdapterTest {
    @Test
    void should_dispatch_detail_scene_handler_when_scene_empty() {
        StubQueryEngine defaultEngine = new StubQueryEngine("default-detail");
        QueryDispatcherAdapter adapter = new QueryDispatcherAdapter(defaultEngine);

        CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(QueryOperation.DETAIL),
            RouteKeyFactory.normalizeScene(null)
        );
        adapter.registerDetailHandler(new QueryDetailSceneHandler<Object>() {
            @Override
            public java.util.Set<CrudRouteKey> routeKeys() {
                return Collections.singleton(routeKey);
            }

            @Override
            public Object handle(QuerySpec<Object> spec, SceneDelegate<QuerySpec<Object>, Object> delegate) {
                return "custom-detail";
            }
        });

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.DETAIL)
            .build();

        Object result = adapter.route(spec).handler().detail(spec);
        Assertions.assertEquals("custom-detail", result);
        Assertions.assertEquals(0, defaultEngine.detailCalls);
    }

    @Test
    void should_fallback_default_engine_for_detail_when_scene_empty_without_handler() {
        StubQueryEngine defaultEngine = new StubQueryEngine("default-detail");
        QueryDispatcherAdapter adapter = new QueryDispatcherAdapter(defaultEngine);

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.DETAIL)
            .build();

        Object result = adapter.route(spec).handler().detail(spec);
        Assertions.assertEquals("default-detail", result);
        Assertions.assertEquals(1, defaultEngine.detailCalls);
    }

    @Test
    void should_fail_for_detail_when_scene_miss() {
        StubQueryEngine defaultEngine = new StubQueryEngine("default-detail");
        QueryDispatcherAdapter adapter = new QueryDispatcherAdapter(defaultEngine);

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.DETAIL)
            .scene("missing.scene")
            .build();

        Assertions.assertThrows(RouteNotFoundException.class, () -> adapter.route(spec));
        Assertions.assertEquals(0, defaultEngine.detailCalls);
    }

    @Test
    void should_dispatch_find_one_handler_and_fail_when_scene_missing() {
        StubQueryEngine defaultEngine = new StubQueryEngine("default-find-one");
        QueryDispatcherAdapter adapter = new QueryDispatcherAdapter(defaultEngine);

        CrudRouteKey routeKey = new CrudRouteKey(
            Collections.singletonList(TestEntity.class.getName()),
            CrudOperationKey.of(QueryOperation.FIND_ONE),
            RouteKeyFactory.normalizeScene("custom.find.one")
        );
        adapter.registerFindOneHandler(new QueryFindOneSceneHandler<Object>() {
            @Override
            public java.util.Set<CrudRouteKey> routeKeys() {
                return Collections.singleton(routeKey);
            }

            @Override
            public Object handle(QuerySpec<Object> spec, SceneDelegate<QuerySpec<Object>, Object> delegate) {
                return "custom-find-one";
            }
        });

        QuerySpec<Object> hitSpec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.FIND_ONE)
            .scene("custom.find.one")
            .build();
        Object hitResult = adapter.route(hitSpec).handler().findOne(hitSpec);
        Assertions.assertEquals("custom-find-one", hitResult);
        Assertions.assertEquals(0, defaultEngine.findOneCalls);

        QuerySpec<Object> missSpec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.FIND_ONE)
            .scene("missing.scene")
            .build();
        Assertions.assertThrows(RouteNotFoundException.class, () -> adapter.route(missSpec));
        Assertions.assertEquals(0, defaultEngine.findOneCalls);
    }

    @Test
    void should_fail_for_page_scene_miss() {
        QueryDispatcherAdapter adapter = new QueryDispatcherAdapter(new StubQueryEngine("default-page"));

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.PAGE)
            .scene("missing.scene")
            .build();

        Assertions.assertThrows(RouteNotFoundException.class, () -> adapter.route(spec));
    }

    @Test
    void should_fail_for_list_scene_miss() {
        QueryDispatcherAdapter adapter = new QueryDispatcherAdapter(new StubQueryEngine("default-list"));

        QuerySpec<Object> spec = QuerySpec.<Object>builder()
            .rootType(TestEntity.class)
            .entityClasses(Collections.<Class<?>>singletonList(TestEntity.class))
            .op(QueryOperation.LIST)
            .scene("missing.scene")
            .build();

        Assertions.assertThrows(RouteNotFoundException.class, () -> adapter.route(spec));
    }

    private static final class StubQueryEngine implements QueryEngine {
        private final Object detailValue;
        private int detailCalls;
        private int findOneCalls;

        private StubQueryEngine(Object detailValue) {
            this.detailValue = detailValue;
        }

        @Override
        public <R> PageResult<R> page(QuerySpec<R> spec) {
            return null;
        }

        @Override
        public <R> List<R> list(QuerySpec<R> spec) {
            return Collections.<R>emptyList();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> R findOne(QuerySpec<R> spec) {
            findOneCalls++;
            return (R) detailValue;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> R detail(QuerySpec<R> spec) {
            detailCalls++;
            return (R) detailValue;
        }
    }

    private static final class TestEntity {
    }
}
