package com.example.minicommerce.order.dto;

import java.math.BigDecimal;
import java.util.List;

/** 一次下单生成的订单 ID 列表及合计金额；每个商家对应一笔订单。 */
public record PlaceOrderResult(List<Long> orderIds, BigDecimal totalAmount) {
}
