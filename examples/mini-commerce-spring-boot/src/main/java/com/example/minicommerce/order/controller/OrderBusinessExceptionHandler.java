package com.example.minicommerce.order.controller;

import com.entloom.crud.starter.web.controller.EntCrudCommandController;
import com.entloom.crud.starter.web.controller.EntCrudQueryController;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.exception.OrderValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将订单 Handler 的业务异常转换为稳定 HTTP 错误，框架异常仍由统一转换器处理。 */
@RestControllerAdvice(assignableTypes = {EntCrudCommandController.class, EntCrudQueryController.class})
public class OrderBusinessExceptionHandler {
    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ErrorResponse> handle(OrderValidationException exception) {
        HttpStatus status = exception.getError() == OrderError.ORDER_NOT_FOUND
            ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    /** 业务错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
