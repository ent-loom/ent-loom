package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;

/**
 * 普通实体 UPDATE 场景的强类型模板基类。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class AbstractEntityUpdateHandler<T, R>
    extends AbstractEntityCommandHandler<T, R> {

    @Override
    public final CommandOperation operation() {
        return CommandOperation.UPDATE;
    }
}
