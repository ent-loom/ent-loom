package com.example.minicommerce.order.dto;

/** 订单用例读取的当前客户信息，不代表持久化历史快照。 */
public record OrderCustomerInfo(Long id, String displayName) {
}
