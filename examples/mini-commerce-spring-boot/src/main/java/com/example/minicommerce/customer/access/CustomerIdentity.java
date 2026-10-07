package com.example.minicommerce.customer.access;

import com.entloom.crud.api.access.BusinessIdentity;

/** 当前用户绑定的客户身份，由服务端账号归属解析。 */
public record CustomerIdentity(Long customerId) implements BusinessIdentity {
    public static final String CODE = "customer";

    @Override
    public String getIdentityCode() {
        return CODE;
    }

    @Override
    public String getIdentityId() {
        return customerId.toString();
    }
}
