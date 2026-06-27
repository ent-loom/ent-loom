package com.entloom.crud.core.capability.query.scene;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.exception.RouteNotFoundException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.capability.query.handler.QueryHandler;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.router.DefaultQueryRoute;
import com.entloom.crud.core.runtime.router.QueryRoute;
import com.entloom.crud.core.runtime.router.QueryRouter;
import com.entloom.crud.core.runtime.scene.DefaultSceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.runtime.scene.SceneHandlerRegistry;
import com.entloom.crud.core.runtime.scene.UnifiedSceneDispatcher;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.enums.QueryStrategy;
import java.util.List;

/**
 * Query 分域分发适配器。
 */
public class QueryDispatcherAdapter implements QueryRouter {
    private final QueryEngine defaultQueryEngine;
    private final SceneHandlerRegistry<QuerySpec<Object>, PageResult<Object>> pageRegistry;
    private final SceneHandlerRegistry<QuerySpec<Object>, List<Object>> listRegistry;
    private final SceneHandlerRegistry<QuerySpec<Object>, Object> findOneRegistry;
    private final SceneHandlerRegistry<QuerySpec<Object>, Object> detailRegistry;
    private final UnifiedSceneDispatcher<QuerySpec<Object>, PageResult<Object>> pageDispatcher;
    private final UnifiedSceneDispatcher<QuerySpec<Object>, List<Object>> listDispatcher;
    private final UnifiedSceneDispatcher<QuerySpec<Object>, Object> findOneDispatcher;
    private final UnifiedSceneDispatcher<QuerySpec<Object>, Object> detailDispatcher;

    public QueryDispatcherAdapter(QueryEngine defaultQueryEngine) {
        this(
            defaultQueryEngine,
            new DefaultSceneHandlerRegistry<QuerySpec<Object>, PageResult<Object>>(),
            new DefaultSceneHandlerRegistry<QuerySpec<Object>, List<Object>>(),
            new DefaultSceneHandlerRegistry<QuerySpec<Object>, Object>(),
            new DefaultSceneHandlerRegistry<QuerySpec<Object>, Object>()
        );
    }

    public QueryDispatcherAdapter(
        QueryEngine defaultQueryEngine,
        SceneHandlerRegistry<QuerySpec<Object>, PageResult<Object>> pageRegistry,
        SceneHandlerRegistry<QuerySpec<Object>, List<Object>> listRegistry,
        SceneHandlerRegistry<QuerySpec<Object>, Object> detailRegistry
    ) {
        this(defaultQueryEngine, pageRegistry, listRegistry, new DefaultSceneHandlerRegistry<QuerySpec<Object>, Object>(), detailRegistry);
    }

    public QueryDispatcherAdapter(
        QueryEngine defaultQueryEngine,
        SceneHandlerRegistry<QuerySpec<Object>, PageResult<Object>> pageRegistry,
        SceneHandlerRegistry<QuerySpec<Object>, List<Object>> listRegistry,
        SceneHandlerRegistry<QuerySpec<Object>, Object> findOneRegistry,
        SceneHandlerRegistry<QuerySpec<Object>, Object> detailRegistry
    ) {
        if (defaultQueryEngine == null) {
            throw new ValidationException("defaultQueryEngine 不能为空");
        }
        this.defaultQueryEngine = defaultQueryEngine;
        this.pageRegistry = pageRegistry;
        this.listRegistry = listRegistry;
        this.findOneRegistry = findOneRegistry;
        this.detailRegistry = detailRegistry;
        this.pageDispatcher = new UnifiedSceneDispatcher<QuerySpec<Object>, PageResult<Object>>(pageRegistry);
        this.listDispatcher = new UnifiedSceneDispatcher<QuerySpec<Object>, List<Object>>(listRegistry);
        this.findOneDispatcher = new UnifiedSceneDispatcher<QuerySpec<Object>, Object>(findOneRegistry);
        this.detailDispatcher = new UnifiedSceneDispatcher<QuerySpec<Object>, Object>(detailRegistry);
    }

    public void registerPageHandler(QueryPageSceneHandler<?> handler) {
        pageRegistry.register(castPageHandler(handler));
    }

    public void registerListHandler(QueryListSceneHandler<?> handler) {
        listRegistry.register(castListHandler(handler));
    }

    public void registerFindOneHandler(QueryFindOneSceneHandler<?> handler) {
        findOneRegistry.register(castFindOneHandler(handler));
    }

    public void registerDetailHandler(QueryDetailSceneHandler<?> handler) {
        detailRegistry.register(castDetailHandler(handler));
    }

    @Override
    public <R> QueryRoute<R> route(QuerySpec<R> spec) {
        if (spec == null) {
            throw new ValidationException("spec 不能为空");
        }
        if (spec.getOp() == null) {
            throw new ValidationException("Query spec.op 不能为空");
        }
        CrudRouteKey routeKey = RouteKeyFactory.buildQueryRoute(spec);
        switch (spec.getOp()) {
            case PAGE:
                return pageRoute(spec, routeKey);
            case LIST:
                return listRoute(spec, routeKey);
            case FIND_ONE:
                return findOneRoute(spec, routeKey);
            case DETAIL:
                return detailRoute(spec, routeKey);
            default:
                throw new ValidationException("不支持的 Query op: " + spec.getOp());
        }
    }

    @SuppressWarnings("unchecked")
    private <R> QueryRoute<R> pageRoute(final QuerySpec<R> spec, final CrudRouteKey routeKey) {
        final SceneHandler<QuerySpec<Object>, PageResult<Object>> sceneHandler = pageRegistry.resolveOrNull(routeKey);
        if (sceneHandler == null) {
            return defaultRouteOrFail(routeKey);
        }
        QueryStrategy strategy = resolveDefaultStrategy(sceneHandler);
        QueryHandler<R> handler = new QueryHandler<R>() {
            @Override
            public boolean supports(QuerySpec<R> querySpec) {
                return true;
            }

            @Override
            public PageResult<R> page(QuerySpec<R> querySpec) {
                PageResult<Object> result = pageDispatcher.dispatch(
                    routeKey,
                    (QuerySpec<Object>) querySpec,
                    new SceneDelegate<QuerySpec<Object>, PageResult<Object>>() {
                        @Override
                        public PageResult<Object> invoke(QuerySpec<Object> delegateSpec) {
                            return (PageResult<Object>) defaultQueryEngine.page(delegateSpec);
                        }
                    }
                );
                return (PageResult<R>) result;
            }

            @Override
            public List<R> list(QuerySpec<R> querySpec) {
                return defaultQueryEngine.list(querySpec);
            }

            @Override
            public R findOne(QuerySpec<R> querySpec) {
                return defaultQueryEngine.findOne(querySpec);
            }

            @Override
            public R detail(QuerySpec<R> querySpec) {
                return defaultQueryEngine.detail(querySpec);
            }
        };
        return new DefaultQueryRoute<R>(handler, strategy);
    }

    @SuppressWarnings("unchecked")
    private <R> QueryRoute<R> listRoute(final QuerySpec<R> spec, final CrudRouteKey routeKey) {
        final SceneHandler<QuerySpec<Object>, List<Object>> sceneHandler = listRegistry.resolveOrNull(routeKey);
        if (sceneHandler == null) {
            return defaultRouteOrFail(routeKey);
        }
        QueryStrategy strategy = resolveDefaultStrategy(sceneHandler);
        QueryHandler<R> handler = new QueryHandler<R>() {
            @Override
            public boolean supports(QuerySpec<R> querySpec) {
                return true;
            }

            @Override
            public PageResult<R> page(QuerySpec<R> querySpec) {
                return defaultQueryEngine.page(querySpec);
            }

            @Override
            public List<R> list(QuerySpec<R> querySpec) {
                List<Object> result = listDispatcher.dispatch(
                    routeKey,
                    (QuerySpec<Object>) querySpec,
                    new SceneDelegate<QuerySpec<Object>, List<Object>>() {
                        @Override
                        public List<Object> invoke(QuerySpec<Object> delegateSpec) {
                            return (List<Object>) defaultQueryEngine.list(delegateSpec);
                        }
                    }
                );
                return (List<R>) result;
            }

            @Override
            public R findOne(QuerySpec<R> querySpec) {
                return defaultQueryEngine.findOne(querySpec);
            }

            @Override
            public R detail(QuerySpec<R> querySpec) {
                return defaultQueryEngine.detail(querySpec);
            }
        };
        return new DefaultQueryRoute<R>(handler, strategy);
    }

    @SuppressWarnings("unchecked")
    private <R> QueryRoute<R> findOneRoute(final QuerySpec<R> spec, final CrudRouteKey routeKey) {
        final SceneHandler<QuerySpec<Object>, Object> sceneHandler = findOneRegistry.resolveOrNull(routeKey);
        if (sceneHandler == null) {
            return defaultRouteOrFail(routeKey);
        }
        QueryStrategy strategy = resolveDefaultStrategy(sceneHandler);
        QueryHandler<R> handler = new QueryHandler<R>() {
            @Override
            public boolean supports(QuerySpec<R> querySpec) {
                return true;
            }

            @Override
            public PageResult<R> page(QuerySpec<R> querySpec) {
                return defaultQueryEngine.page(querySpec);
            }

            @Override
            public List<R> list(QuerySpec<R> querySpec) {
                return defaultQueryEngine.list(querySpec);
            }

            @Override
            public R findOne(QuerySpec<R> querySpec) {
                Object result = findOneDispatcher.dispatch(
                    routeKey,
                    (QuerySpec<Object>) querySpec,
                    new SceneDelegate<QuerySpec<Object>, Object>() {
                        @Override
                        public Object invoke(QuerySpec<Object> delegateSpec) {
                            return defaultQueryEngine.findOne(delegateSpec);
                        }
                    }
                );
                return (R) result;
            }

            @Override
            public R detail(QuerySpec<R> querySpec) {
                return defaultQueryEngine.detail(querySpec);
            }
        };
        return new DefaultQueryRoute<R>(handler, strategy);
    }

    @SuppressWarnings("unchecked")
    private <R> QueryRoute<R> detailRoute(final QuerySpec<R> spec, final CrudRouteKey routeKey) {
        final SceneHandler<QuerySpec<Object>, Object> sceneHandler = detailRegistry.resolveOrNull(routeKey);
        if (sceneHandler == null) {
            return defaultRouteOrFail(routeKey);
        }
        QueryStrategy strategy = resolveDefaultStrategy(sceneHandler);
        QueryHandler<R> handler = new QueryHandler<R>() {
            @Override
            public boolean supports(QuerySpec<R> querySpec) {
                return true;
            }

            @Override
            public PageResult<R> page(QuerySpec<R> querySpec) {
                return defaultQueryEngine.page(querySpec);
            }

            @Override
            public List<R> list(QuerySpec<R> querySpec) {
                return defaultQueryEngine.list(querySpec);
            }

            @Override
            public R findOne(QuerySpec<R> querySpec) {
                return defaultQueryEngine.findOne(querySpec);
            }

            @Override
            public R detail(QuerySpec<R> querySpec) {
                Object result = detailDispatcher.dispatch(
                    routeKey,
                    (QuerySpec<Object>) querySpec,
                    new SceneDelegate<QuerySpec<Object>, Object>() {
                        @Override
                        public Object invoke(QuerySpec<Object> delegateSpec) {
                            return defaultQueryEngine.detail(delegateSpec);
                        }
                    }
                );
                return (R) result;
            }
        };
        return new DefaultQueryRoute<R>(handler, strategy);
    }

    private <R> QueryRoute<R> defaultRoute() {
        QueryHandler<R> handler = new QueryHandler<R>() {
            @Override
            public boolean supports(QuerySpec<R> spec) {
                return true;
            }

            @Override
            public PageResult<R> page(QuerySpec<R> spec) {
                return defaultQueryEngine.page(spec);
            }

            @Override
            public List<R> list(QuerySpec<R> spec) {
                return defaultQueryEngine.list(spec);
            }

            @Override
            public R findOne(QuerySpec<R> spec) {
                return defaultQueryEngine.findOne(spec);
            }

            @Override
            public R detail(QuerySpec<R> spec) {
                return defaultQueryEngine.detail(spec);
            }
        };
        return new DefaultQueryRoute<R>(handler, QueryStrategy.DEFAULT);
    }

    private <R> QueryRoute<R> defaultRouteOrFail(CrudRouteKey routeKey) {
        if (routeKey.getScene().isEmpty()) {
            return defaultRoute();
        }
        throw new RouteNotFoundException("未找到查询路由: " + routeKey);
    }

    @SuppressWarnings("unchecked")
    private SceneHandler<QuerySpec<Object>, PageResult<Object>> castPageHandler(QueryPageSceneHandler<?> handler) {
        return (SceneHandler<QuerySpec<Object>, PageResult<Object>>) (SceneHandler<?, ?>) handler;
    }

    @SuppressWarnings("unchecked")
    private SceneHandler<QuerySpec<Object>, List<Object>> castListHandler(QueryListSceneHandler<?> handler) {
        return (SceneHandler<QuerySpec<Object>, List<Object>>) (SceneHandler<?, ?>) handler;
    }

    @SuppressWarnings("unchecked")
    private SceneHandler<QuerySpec<Object>, Object> castFindOneHandler(QueryFindOneSceneHandler<?> handler) {
        return (SceneHandler<QuerySpec<Object>, Object>) (SceneHandler<?, ?>) handler;
    }

    @SuppressWarnings("unchecked")
    private SceneHandler<QuerySpec<Object>, Object> castDetailHandler(QueryDetailSceneHandler<?> handler) {
        return (SceneHandler<QuerySpec<Object>, Object>) (SceneHandler<?, ?>) handler;
    }

    private QueryStrategy resolveDefaultStrategy(SceneHandler<?, ?> handler) {
        if (handler instanceof QuerySceneHandler<?>) {
            return ((QuerySceneHandler<?>) handler).defaultStrategy();
        }
        return QueryStrategy.DEFAULT;
    }

}
