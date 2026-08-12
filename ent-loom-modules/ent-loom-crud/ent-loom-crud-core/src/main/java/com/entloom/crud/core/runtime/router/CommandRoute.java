package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.command.handler.CommandHandler;

/**
 * 命令路由结果。
 *
 * @param <P> 入参类型
 * @param <R> 返回类型
 */
public interface CommandRoute<P, R> {
    /**
     * 取得处理器。
     *
     * @return 命令处理器
     */
    CommandHandler<P, R> handler();
}
