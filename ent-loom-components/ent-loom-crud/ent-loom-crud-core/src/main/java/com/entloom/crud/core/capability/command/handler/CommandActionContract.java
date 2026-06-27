package com.entloom.crud.core.capability.command.handler;

import com.entloom.crud.core.exception.ValidationException;
import lombok.Getter;

/**
 * ACTION scene 命令契约，定义请求与响应数据类型。
 */
@Getter
public final class CommandActionContract {
    /** 请求类型。 */
    private final Class<?> requestType;
    /** 响应类型。 */
    private final Class<?> responseType;

    public CommandActionContract(Class<?> requestType, Class<?> responseType) {
        if (requestType == null) {
            throw new ValidationException("requestType 不能为空");
        }
        if (responseType == null) {
            throw new ValidationException("responseType 不能为空");
        }
        this.requestType = requestType;
        this.responseType = responseType;
    }
}
