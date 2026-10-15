package com.example.minicommerce.common.access;

import com.entloom.crud.annotations.EntCrudPolicy;

/** 商家可读取、维护的本人数据；具体可写字段由各实体的字段授权声明。 */
@EntCrudPolicy(template = "merchant-manage")
public interface MerchantManagePolicy {
    Long getMerchantId();
}
