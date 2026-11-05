package com.example.minicommerce.merchant.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("商户微信设置")
@EntCrudEntity(httpExposed = false)
@Getter
@Setter
@NoArgsConstructor
public class MerchantWxSetting {
    @EntField("设置 ID")
    private Long id;

    @EntField("所属商户 ID")
    @EntRelation(Merchant.class)
    private Long merchantId;

    @EntField("公众号appId")
    private String appId;

    @EntField("公众号appSecret")
    private String appSecret;
}
