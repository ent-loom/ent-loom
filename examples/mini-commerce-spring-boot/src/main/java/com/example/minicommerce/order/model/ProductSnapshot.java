package com.example.minicommerce.order.model;

import java.math.BigDecimal;

/** 下单时读取的商品快照。 */
public record ProductSnapshot(Long id, String name, BigDecimal price, boolean active) {
}
