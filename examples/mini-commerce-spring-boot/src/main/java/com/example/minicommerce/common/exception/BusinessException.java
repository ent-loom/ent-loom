package com.example.minicommerce.common.exception;

import com.entloom.crud.starter.web.error.EntBusinessException;
import org.springframework.http.HttpStatus;

/** 应用通用业务异常，由框架全局处理器转换为 HTTP 响应。 */
public class BusinessException extends EntBusinessException {
    private final BusinessError error;

    public BusinessException(BusinessError error) {
        super(error.message());
        this.error = error;
    }

    public BusinessException(BusinessError error, Throwable cause) {
        this(error);
        initCause(cause);
    }

    @Override
    public String getCode() {
        return error.code();
    }

    @Override
    public HttpStatus getHttpStatus() {
        return error.httpStatus();
    }
}
