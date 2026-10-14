package com.example.minicommerce.order.model;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 客户与商家分别按本人归属读取的订单。 */
@EntCrudPolicy(template = "order-entries")
public interface OrderEntries {
    Long getCustomerId();

    Long getMerchantId();
}
