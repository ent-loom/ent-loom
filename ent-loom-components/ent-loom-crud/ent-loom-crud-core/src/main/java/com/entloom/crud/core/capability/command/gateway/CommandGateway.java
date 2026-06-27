package com.entloom.crud.core.capability.command.gateway;

import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * 命令网关。
 */
public interface CommandGateway {
    /**
     * 执行业务动作。
     *
     * @param spec 命令协议
     * @param <P> 入参类型
     * @param <R> 返回类型
     * @return 执行结果
     */
    <P, R> R action(CommandSpec<P> spec);
}
