package com.entloom.crud.core.runtime.scene;

/**
 * 场景处理器标准接口。
 *
 * @param <SPEC> 请求规格类型
 * @param <RESULT> 结果类型
 */
public interface SceneHandler<SPEC, RESULT> extends RouteScopedHandler {
    /**
     * 执行场景处理逻辑。
     */
    RESULT handle(SPEC spec, SceneDelegate<SPEC, RESULT> delegate);
}

