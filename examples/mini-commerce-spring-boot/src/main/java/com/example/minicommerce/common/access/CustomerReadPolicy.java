package com.example.minicommerce.common.access;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 客户只读的本人记录。 */
@EntCrudPolicy(template = "customer-read")
public interface CustomerReadPolicy {
    Long getCustomerId();
}
