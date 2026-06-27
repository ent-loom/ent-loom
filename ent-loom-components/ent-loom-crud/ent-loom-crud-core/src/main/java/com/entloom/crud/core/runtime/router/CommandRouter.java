package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * 命令路由器。
 */
public interface CommandRouter {
    /**
     * 路由命令处理器。
     *
     * @param spec 命令 spec
     * @param <P> 入参类型
     * @param <R> 返回类型
     * @return 路由结果
     */
    <P, R> CommandRoute<P, R> route(CommandSpec<P> spec);
}
