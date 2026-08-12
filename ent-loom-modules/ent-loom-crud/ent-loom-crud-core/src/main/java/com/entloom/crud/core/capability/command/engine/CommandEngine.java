package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.engine.EngineCapability;

/**
 * 默认命令引擎。
 */
public interface CommandEngine {
    /**
     * 当前命令引擎声明的能力。
     *
     * @return 引擎能力
     */
    default EngineCapability capability() {
        return EngineCapability.unknown(getClass().getName());
    }

    /**
     * 执行业务动作。
     *
     * @param spec 命令 spec
     * @param <P> 入参类型
     * @param <R> 返回类型
     * @return 执行结果
     */
    <P, R> R action(CommandSpec<P> spec);
}
