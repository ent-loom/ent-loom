package com.example.minicommerce.order.enums;

/** 订单业务错误码及默认中文提示。 */
public enum OrderError {
    /** 请求对象为空。 */
    REQUEST_REQUIRED("REQUEST_REQUIRED", "下单请求不能为空"),
    /** 下单客户主键为空。 */
    CUSTOMER_REQUIRED("CUSTOMER_REQUIRED", "下单客户不能为空"),
    /** 下单客户不存在。 */
    CUSTOMER_NOT_FOUND("CUSTOMER_NOT_FOUND", "客户不存在"),
    /** 订单商品行为空。 */
    ITEMS_REQUIRED("ITEMS_REQUIRED", "订单至少需要一件商品"),
    /** 商品行对象为空。 */
    ITEM_REQUIRED("ITEM_REQUIRED", "商品行不能为空"),
    /** 商品主键为空。 */
    PRODUCT_REQUIRED("PRODUCT_REQUIRED", "商品不能为空"),
    /** 购买数量为空。 */
    QUANTITY_REQUIRED("QUANTITY_REQUIRED", "购买数量不能为空"),
    /** 购买数量必须为正数。 */
    QUANTITY_INVALID("QUANTITY_INVALID", "购买数量必须大于零"),
    /** 同一订单重复提交商品。 */
    DUPLICATE_PRODUCT("DUPLICATE_PRODUCT", "同一订单不能重复提交商品"),
    /** 商品不存在。 */
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "商品不存在"),
    /** 商品当前未启用。 */
    PRODUCT_INACTIVE("PRODUCT_INACTIVE", "商品当前不可下单"),
    /** 商品价格不符合下单要求。 */
    PRODUCT_PRICE_INVALID("PRODUCT_PRICE_INVALID", "商品价格必须大于零"),
    /** 订单不存在。 */
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在");

    private final String code;
    private final String message;

    OrderError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
