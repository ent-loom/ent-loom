package com.example.minicommerce.merchant.access;

import com.entloom.crud.api.access.BusinessIdentity;

/** 当前商家快照；由服务端账号归属解析，不接受请求指定商家。 */
public record MerchantIdentity(Long merchantId) implements BusinessIdentity {
    public static final String CODE = "merchant";

    @Override
    public String getIdentityCode() {
        return CODE;
    }

    @Override
    public String getIdentityId() {
        return merchantId.toString();
    }
}
