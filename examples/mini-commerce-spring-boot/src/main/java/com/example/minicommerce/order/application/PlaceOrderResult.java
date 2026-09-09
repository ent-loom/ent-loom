package com.example.minicommerce.order.application;

import java.math.BigDecimal;

/** 下单成功后的最小返回值。 */
public record PlaceOrderResult(Long orderId, BigDecimal totalAmount) {
}
