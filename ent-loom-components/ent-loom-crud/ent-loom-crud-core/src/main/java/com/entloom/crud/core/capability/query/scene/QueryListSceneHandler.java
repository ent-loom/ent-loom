package com.entloom.crud.core.capability.query.scene;

import com.entloom.crud.core.runtime.scene.SceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import java.util.List;

/**
 * Query LIST 场景处理器。
 */
public interface QueryListSceneHandler<R> extends QuerySceneHandler<R>, SceneHandler<QuerySpec<R>, List<R>> {
}

