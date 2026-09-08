package com.example.minicommerce.commerce;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将业务校验失败转换为稳定、可观察的 HTTP 错误。 */
@RestControllerAdvice
public class CommerceExceptionHandler {
    @ExceptionHandler(CommerceValidationException.class)
    public ResponseEntity<ErrorResponse> handle(CommerceValidationException exception) {
        HttpStatus status = "ORDER_NOT_FOUND".equals(exception.getCode())
            ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    /** 业务错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
