package com.example.minicommerce.order.exception;

import com.example.minicommerce.order.enums.OrderError;
import com.entloom.crud.starter.web.error.EntBusinessException;

/** 下单业务校验失败。 */
public class OrderValidationException extends EntBusinessException {
    private final OrderError error;

    public OrderValidationException(OrderError error) {
        super(error.message());
        this.error = error;
    }

    @Override
    public String getCode() {
        return error.code();
    }

    public OrderError getError() {
        return error;
    }
}
