package com.example.minicommerce.order.exception;

import com.example.minicommerce.order.enums.OrderError;
import com.entloom.crud.starter.web.error.EntBusinessException;
import org.springframework.http.HttpStatus;

/** 下单业务校验失败。 */
public class OrderValidationException extends EntBusinessException {
    private final OrderError error;

    public OrderValidationException(OrderError error) {
        super(error.message());
        this.error = error;
    }

    public String getCode() {
        return error.code();
    }

    public OrderError getError() {
        return error;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return error == OrderError.ORDER_NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    }
}
