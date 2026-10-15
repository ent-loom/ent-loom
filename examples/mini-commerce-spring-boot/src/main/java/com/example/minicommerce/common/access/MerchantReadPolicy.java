package com.example.minicommerce.common.access;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 商家只读的本人记录。 */
@EntCrudPolicy(template = "merchant-read")
public interface MerchantReadPolicy {
    Long getMerchantId();
}
