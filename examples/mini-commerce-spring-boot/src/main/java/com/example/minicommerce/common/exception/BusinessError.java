package com.example.minicommerce.common.exception;

import org.springframework.http.HttpStatus;

/** 各业务模块错误枚举的公共契约。 */
public interface BusinessError {
    String code();

    String message();

    default HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
