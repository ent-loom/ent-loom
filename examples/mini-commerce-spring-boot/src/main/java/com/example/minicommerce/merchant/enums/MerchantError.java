package com.example.minicommerce.merchant.enums;

import com.example.minicommerce.common.exception.BusinessError;
import org.springframework.http.HttpStatus;

/** 商家业务错误码及默认中文提示。 */
public enum MerchantError implements BusinessError {
    /** 商品不存在、不属于当前商家或版本已变化。 */
    PRICE_UPDATE_REJECTED("商品不可更新或版本已变化，整批未生效；请重新读取本人商品", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus httpStatus;

    MerchantError(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
