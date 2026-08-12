package com.entloom.crud.core.runtime.router;

import com.entloom.crud.core.capability.command.handler.CommandHandler;
import lombok.RequiredArgsConstructor;

/**
 * 简单命令路由结果。
 *
 * @param <P> 入参类型
 * @param <R> 返回类型
 */
@RequiredArgsConstructor
public class DefaultCommandRoute<P, R> implements CommandRoute<P, R> {
    /** 处理器。 */
    private final CommandHandler<P, R> handler;

    @Override
    public CommandHandler<P, R> handler() {
        return handler;
    }
}
