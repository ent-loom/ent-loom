package com.entloom.crud.starter.web.error;

import org.springframework.http.HttpStatus;

/**
 * 可由公共 HTTP 层处理的业务异常契约。
 */
public abstract class EntBusinessException extends RuntimeException {
    protected EntBusinessException(String message) {
        super(message);
    }

    public abstract String getCode();

    /**
     * 默认业务校验失败，业务异常可按需覆盖。
     */
    public HttpStatus getHttpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
