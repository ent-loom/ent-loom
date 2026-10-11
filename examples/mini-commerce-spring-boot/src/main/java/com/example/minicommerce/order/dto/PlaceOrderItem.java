package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 下单请求中的商家商品行。 */
public record PlaceOrderItem(
    /** 商家商品主键；服务端据此确定商家归属。 */
    @NotNull(message = "商家商品不能为空") Long merchantProductId,
    /** 购买数量。 */
    @NotNull(message = "购买数量不能为空")
    @Positive(message = "购买数量必须大于零") Integer quantity
) {
}
