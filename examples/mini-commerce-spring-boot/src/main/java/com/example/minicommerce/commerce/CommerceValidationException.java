package com.example.minicommerce.commerce;

/** 下单业务校验失败。 */
public class CommerceValidationException extends RuntimeException {
    private final String code;

    public CommerceValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
