package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 命令执行结果。
 *
 * @param <T> 业务结果类型
 */
@Getter
@Setter
public class CommandResult<T> {
    /** 是否成功。 */
    private boolean success;
    /** 响应码。 */
    private String code;
    /** 响应消息。 */
    private String message;
    /** 业务数据。 */
    private T data;
    /** 是否为幂等重放结果。 */
    private boolean idempotentReplay;

    public static <T> CommandResult<T> success(T data) {
        CommandResult<T> result = new CommandResult<>();
        result.success = true;
        result.code = "OK";
        result.data = data;
        return result;
    }
}
