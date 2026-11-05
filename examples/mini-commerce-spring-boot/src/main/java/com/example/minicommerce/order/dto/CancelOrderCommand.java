package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.NotNull;

/** 仅传目标订单，归属与状态由服务端校验。 */
public record CancelOrderCommand(@NotNull(message = "订单 ID 不能为空") Long orderId) { }
