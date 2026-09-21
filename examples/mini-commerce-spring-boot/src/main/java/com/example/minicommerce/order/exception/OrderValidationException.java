package com.example.minicommerce.order.exception;

import com.example.minicommerce.order.enums.OrderError;

/** 下单业务校验失败。 */
public class OrderValidationException extends RuntimeException {
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
}
