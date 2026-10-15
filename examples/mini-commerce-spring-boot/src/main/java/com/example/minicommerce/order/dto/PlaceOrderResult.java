package com.example.minicommerce.order.dto;

import java.math.BigDecimal;
import java.util.List;

/** 下单结果：每个商家生成一张独立订单，合计金额仅用于本次响应。 */
public record PlaceOrderResult(List<Long> orderIds, BigDecimal totalAmount) {
}
