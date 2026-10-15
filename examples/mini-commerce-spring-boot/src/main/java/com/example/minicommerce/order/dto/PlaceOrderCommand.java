package com.example.minicommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 客户下单请求；客户和商家归属均由服务端确定。 */
public record PlaceOrderCommand(
    /** 订单商品行。 */
    @NotEmpty(message = "订单至少需要一件商品")
    @Valid
    List<@NotNull(message = "商品行不能为空") PlaceOrderItem> items
) {
}
