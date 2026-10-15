package com.example.minicommerce.access;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 商家只读的本人记录。 */
@EntCrudPolicy(template = "merchant-record")
public interface MerchantRecord {
    Long getMerchantId();
}
