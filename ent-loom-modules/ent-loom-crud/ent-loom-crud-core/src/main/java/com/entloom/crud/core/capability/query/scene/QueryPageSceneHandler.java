package com.entloom.crud.core.capability.query.scene;

import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * Query PAGE 场景处理器。
 */
public interface QueryPageSceneHandler<R> extends QuerySceneHandler<R>, SceneHandler<QuerySpec<R>, PageResult<R>> {
}

