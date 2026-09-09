package com.example.minicommerce.order.model;

import java.math.BigDecimal;

/**
 * 下单时固定的商品和价格信息。
 * @param productId 商品主键
 * @param productName 下单时的商品名称
 * @param unitPrice 下单时的单价
 * @param quantity 购买数量
 * @param lineAmount 明细金额
 */
public record OrderItemSnapshot(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal lineAmount
) {
}
