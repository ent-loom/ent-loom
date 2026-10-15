package com.example.minicommerce.order.enums;

import com.example.minicommerce.common.exception.BusinessError;
import org.springframework.http.HttpStatus;

/** 订单业务错误码及默认中文提示。 */
public enum OrderError implements BusinessError {
    /** 订单不存在或不属于当前访问者。 */
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND),
    /** 请求对象为空。 */
    REQUEST_REQUIRED("REQUEST_REQUIRED", "下单请求不能为空"),
    /** 下单客户不存在。 */
    CUSTOMER_NOT_FOUND("CUSTOMER_NOT_FOUND", "客户不存在"),
    /** 订单商品行为空。 */
    ITEMS_REQUIRED("ITEMS_REQUIRED", "订单至少需要一件商品"),
    /** 商品行对象为空。 */
    ITEM_REQUIRED("ITEM_REQUIRED", "商品行不能为空"),
    /** 商家商品主键为空。 */
    MERCHANT_PRODUCT_REQUIRED("MERCHANT_PRODUCT_REQUIRED", "商家商品不能为空"),
    /** 购买数量为空。 */
    QUANTITY_REQUIRED("QUANTITY_REQUIRED", "购买数量不能为空"),
    /** 购买数量必须为正数。 */
    QUANTITY_INVALID("QUANTITY_INVALID", "购买数量必须大于零"),
    /** 同一订单重复提交商家商品。 */
    DUPLICATE_MERCHANT_PRODUCT("DUPLICATE_MERCHANT_PRODUCT", "同一订单不能重复提交商家商品"),
    /** 商家商品不存在。 */
    MERCHANT_PRODUCT_NOT_FOUND("MERCHANT_PRODUCT_NOT_FOUND", "商家商品不存在"),
    /** 商家商品当前未启用。 */
    MERCHANT_PRODUCT_INACTIVE("MERCHANT_PRODUCT_INACTIVE", "商家商品当前不可下单"),
    /** 商家商品价格不符合下单要求。 */
    MERCHANT_PRODUCT_PRICE_INVALID("MERCHANT_PRODUCT_PRICE_INVALID", "商家商品价格必须大于零");

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    OrderError(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    OrderError(String code, String message, HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
