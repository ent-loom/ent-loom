package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.query.engine.QueryEngine;
import com.entloom.crud.core.capability.query.scene.QueryDetailSceneHandler;
import com.entloom.crud.core.capability.query.scene.QueryDispatcherAdapter;
import com.entloom.crud.core.capability.query.scene.QueryFindOneSceneHandler;
import com.entloom.crud.core.capability.query.scene.QueryListSceneHandler;
import com.entloom.crud.core.capability.query.scene.QueryPageSceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * Query 路由薄适配类：对外保留原类型名，内部走统一场景分发。
 */
public class DefaultQueryRouter implements QueryRouter {
    private final QueryDispatcherAdapter dispatcherAdapter;

    public DefaultQueryRouter(QueryEngine defaultQueryEngine) {
        this.dispatcherAdapter = new QueryDispatcherAdapter(defaultQueryEngine);
    }

    public void registerPageSceneHandler(QueryPageSceneHandler<?> handler) {
        dispatcherAdapter.registerPageHandler(handler);
    }

    public void registerListSceneHandler(QueryListSceneHandler<?> handler) {
        dispatcherAdapter.registerListHandler(handler);
    }

    public void registerFindOneSceneHandler(QueryFindOneSceneHandler<?> handler) {
        dispatcherAdapter.registerFindOneHandler(handler);
    }

    public void registerDetailSceneHandler(QueryDetailSceneHandler<?> handler) {
        dispatcherAdapter.registerDetailHandler(handler);
    }

    @Override
    public <R> QueryRoute<R> route(QuerySpec<R> spec) {
        return dispatcherAdapter.route(spec);
    }
}
