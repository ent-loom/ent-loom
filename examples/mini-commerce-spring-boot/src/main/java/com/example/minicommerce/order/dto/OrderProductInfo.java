package com.example.minicommerce.order.dto;

import java.math.BigDecimal;

/** 订单用例读取的当前商品信息，成交信息由订单明细快照固化。 */
public record OrderProductInfo(Long id, String name, BigDecimal price, boolean active) {
}
