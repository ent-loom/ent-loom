package com.entloom.crud.core.runtime.scene;

/**
 * 场景处理器回调默认执行链。
 *
 * @param <SPEC> 请求规格类型
 * @param <RESULT> 结果类型
 */
public interface SceneDelegate<SPEC, RESULT> {
    /**
     * 调用默认执行链。
     */
    RESULT invoke(SPEC spec);
}

