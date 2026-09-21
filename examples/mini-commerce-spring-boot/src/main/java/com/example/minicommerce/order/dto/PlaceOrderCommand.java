package com.example.minicommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 提交订单的业务命令，不映射为通用 CRUD CREATE。 */
public record PlaceOrderCommand(
    /** 下单客户主键。 */
    @NotNull(message = "下单客户不能为空") Long customerId,
    /** 订单商品行。 */
    @NotEmpty(message = "订单至少需要一件商品")
    @Valid
    List<@NotNull(message = "商品行不能为空") PlaceOrderItem> items
) {
}
