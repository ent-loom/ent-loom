package com.example.minicommerce.order.dto;

import java.math.BigDecimal;
import java.util.List;

/** 客户结算成功后的结果；每个商家都有独立销售订单。 */
public record PlaceOrderResult(Long orderId, List<Long> merchantOrderIds, BigDecimal totalAmount) {
}
