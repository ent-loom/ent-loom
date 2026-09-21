package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 下单请求中的商品行。 */
public record PlaceOrderItem(
    /** 商品主键。 */
    @NotNull(message = "商品不能为空") Long productId,
    /** 购买数量。 */
    @NotNull(message = "购买数量不能为空")
    @Positive(message = "购买数量必须大于零") Integer quantity
) {
}
