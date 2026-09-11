package com.example.minicommerce.order.exception;

/** 下单业务校验失败。 */
public class OrderValidationException extends RuntimeException {
    private final String code;

    public OrderValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
