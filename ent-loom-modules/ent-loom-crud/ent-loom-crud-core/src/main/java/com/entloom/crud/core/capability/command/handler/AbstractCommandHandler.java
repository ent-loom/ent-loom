package com.entloom.crud.core.capability.command.handler;

import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * 命令处理器模板基类。
 *
 * @param <P> 入参类型
 * @param <R> 返回类型
 */
public abstract class AbstractCommandHandler<P, R> implements CommandHandler<P, R> {
    @Override
    public boolean supports(CommandSpec<P> spec) {
        return true;
    }

    @Override
    public final R action(CommandSpec<P> spec) {
        beforeAction(spec);
        R result = doAction(spec);
        afterAction(spec, result);
        return result;
    }

    /**
     * 执行前钩子。
     *
     * @param spec 命令 spec
     */
    protected void beforeAction(CommandSpec<P> spec) {
    }

    /**
     * 业务命令执行。
     *
     * @param spec 命令 spec
     * @return 结果
     */
    protected abstract R doAction(CommandSpec<P> spec);

    /**
     * 执行后钩子。
     *
     * @param spec 命令 spec
     * @param result 执行结果
     */
    protected void afterAction(CommandSpec<P> spec, R result) {
    }
}
