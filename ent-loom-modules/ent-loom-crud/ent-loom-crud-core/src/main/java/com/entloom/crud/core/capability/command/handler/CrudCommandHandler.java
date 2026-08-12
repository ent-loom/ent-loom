package com.entloom.crud.core.capability.command.handler;

import com.entloom.crud.core.capability.command.spec.CommandSpec;

/**
 * 默认 CRUD 命令处理器。
 *
 * @param <P> 入参类型
 * @param <R> 返回类型
 */
public interface CrudCommandHandler<P, R> extends CommandHandler<P, R> {
    /**
     * 执行新增。
     *
     * @param spec 命令 spec
     * @return 结果
     */
    R create(CommandSpec<P> spec);

    /**
     * 执行更新。
     *
     * @param spec 命令 spec
     * @return 结果
     */
    R update(CommandSpec<P> spec);

    /**
     * 执行删除。
     *
     * @param spec 命令 spec
     * @return 结果
     */
    R delete(CommandSpec<P> spec);

    /**
     * 执行批量。
     *
     * @param spec 命令 spec
     * @return 结果
     */
    R batch(CommandSpec<P> spec);
}
