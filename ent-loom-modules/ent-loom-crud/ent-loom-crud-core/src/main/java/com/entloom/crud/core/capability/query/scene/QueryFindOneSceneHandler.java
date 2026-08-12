package com.entloom.crud.core.capability.query.scene;

import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;

/**
 * Query FIND_ONE 场景处理器。
 */
public interface QueryFindOneSceneHandler<R> extends QuerySceneHandler<R>, SceneHandler<QuerySpec<R>, R> {
}
