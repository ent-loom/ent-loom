package com.example.minicommerce.merchant.model;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 商家可读取、维护的本人数据；具体可写字段由各实体的字段授权声明。 */
@EntCrudPolicy(template = "merchant-data")
public interface MerchantData {
    Long getMerchantId();
}
