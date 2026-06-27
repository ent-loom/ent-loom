package com.entloom.crud.core.capability.command.handler;

import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * 命令处理器。
 *
 * @param <P> 入参类型
 * @param <R> 返回类型
 */
public interface CommandHandler<P, R> {
    /**
     * 是否支持当前请求。
     *
     * @param spec 命令 spec
     * @return true 表示支持
     */
    boolean supports(CommandSpec<P> spec);

    /**
     * 执行业务动作。
     *
     * @param spec 命令 spec
     * @return 结果
     */
    R action(CommandSpec<P> spec);
}
