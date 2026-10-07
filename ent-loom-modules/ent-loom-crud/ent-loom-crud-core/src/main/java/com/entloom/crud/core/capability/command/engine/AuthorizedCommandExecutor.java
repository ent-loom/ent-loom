package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.capability.command.spec.CommandSpec;

/** 按次授权、受约束执行与审计的命令入口。 */
public interface AuthorizedCommandExecutor {
    /**
     * 使用本次可信访问者执行命令。
     *
     * @param visitor 可信访问者
     * @param spec 命令规范
     * @param <P> 入参类型
     * @param <R> 返回类型
     * @return 执行结果
     */
    <P, R> R action(VisitorContext visitor, CommandSpec<P> spec);
}
