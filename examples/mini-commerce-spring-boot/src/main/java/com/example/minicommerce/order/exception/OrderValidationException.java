package com.example.minicommerce.order.exception;

import lombok.Getter;

/** 下单业务校验失败。 */
@Getter
public class OrderValidationException extends RuntimeException {
    private final String code;

    public OrderValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

}
