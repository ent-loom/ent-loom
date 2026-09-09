package com.example.minicommerce.order.application;

import java.util.List;

/** 提交订单的业务命令，不映射为通用 CRUD CREATE。 */
public class PlaceOrderCommand {
    /** 下单客户主键。 */
    private Long customerId;

    /** 订单商品行。 */
    private List<PlaceOrderItem> items;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<PlaceOrderItem> getItems() {
        return items;
    }

    public void setItems(List<PlaceOrderItem> items) {
        this.items = items;
    }
}
