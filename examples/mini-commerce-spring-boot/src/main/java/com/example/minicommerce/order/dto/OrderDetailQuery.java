package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 两端共用的订单详情请求，归属由可信身份确定。 */
public record OrderDetailQuery(
    @NotNull(message = "订单 ID 不能为空")
    @Positive(message = "订单 ID 须为正整数") Long orderId
) {
}
