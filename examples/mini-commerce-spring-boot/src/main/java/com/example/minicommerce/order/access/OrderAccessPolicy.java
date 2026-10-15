package com.example.minicommerce.order.access;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 客户可下单并读取本人订单，商家只读本商家订单。 */
@EntCrudPolicy(template = "order-access")
public interface OrderAccessPolicy {
    Long getCustomerId();

    Long getMerchantId();
}
