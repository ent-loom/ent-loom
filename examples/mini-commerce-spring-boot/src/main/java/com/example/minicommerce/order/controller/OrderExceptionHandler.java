package com.example.minicommerce.order.controller;

import com.example.minicommerce.order.exception.OrderValidationException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将业务校验失败转换为稳定、可观察的 HTTP 错误。 */
@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {
    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ErrorResponse> handle(OrderValidationException exception) {
        HttpStatus status = "ORDER_NOT_FOUND".equals(exception.getCode())
            ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> forbidden(PermissionDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("ORDER_ACCESS_DENIED", "当前主体无权执行订单操作"));
    }

    /** 业务错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
