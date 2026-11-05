package com.entloom.crud.core.capability.command.handler;

import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.exception.CommandActionRejectedException;
import com.entloom.crud.core.exception.CrudException;

/** Handler 边界在事务内校验；Gateway 复用同一规则作防御检查。 */
public final class CommandActionResults {
    private CommandActionResults() { }

    @SuppressWarnings("unchecked")
    public static <R> CommandResult<R> requireSuccess(CommandResult<?> result, Class<R> responseType) {
        if (result == null) throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "业务动作未返回结果");
        if (!result.isSuccess()) throw new CommandActionRejectedException(result.getCode(), result.getMessage());
        if (result.getData() != null && !responseType.isInstance(result.getData())) {
            throw new CrudException(CrudErrorCode.INTERNAL_ERROR, "业务动作响应类型与注册契约不匹配");
        }
        return (CommandResult<R>) result;
    }
}
