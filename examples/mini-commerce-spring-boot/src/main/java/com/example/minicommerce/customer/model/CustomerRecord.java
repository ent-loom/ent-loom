package com.example.minicommerce.customer.model;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 客户只读的本人记录。 */
@EntCrudPolicy(template = "customer-record")
public interface CustomerRecord {
    Long getCustomerId();
}
