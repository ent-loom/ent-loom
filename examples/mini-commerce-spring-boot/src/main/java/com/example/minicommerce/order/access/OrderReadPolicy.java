package com.example.minicommerce.order.access;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 客户与商家分别按本人归属读取的订单。 */
@EntCrudPolicy(template = "order-read")
public interface OrderReadPolicy {
    Long getCustomerId();

    Long getMerchantId();
}
