package com.example.minicommerce.order.dto;

import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单详情及其明细。 */
public record OrderDetail(
    Long id,
    Long customerId,
    String customerName,
    OrderStatus status,
    BigDecimal totalAmount,
    LocalDateTime createdAt,
    List<Item> items
) {
    /** 订单明细返回值。 */
    public record Item(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount
    ) {
    }
}
