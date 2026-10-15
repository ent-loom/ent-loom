package com.example.minicommerce.order.dto;

import com.entloom.crud.api.model.CrudRecord;
import com.example.minicommerce.order.entity.OrderItem;
import java.util.List;

/** 已校验客户或商家归属的订单及成交明细。 */
public record OrderDetail(CrudRecord order, List<OrderItem> items) {
}
