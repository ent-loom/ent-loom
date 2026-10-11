package com.example.minicommerce.merchant.entity;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商家对商品的独立报价，仅通过商家受治理接口维护。 */
@EntEntity("商家商品报价")
@Getter
@Setter
@NoArgsConstructor
public class MerchantProduct {
    @EntField("报价 ID")
    private Long id;

    @EntField(value = "商品 ID", readOnly = OptionalBoolean.TRUE)
    private Long productId;

    @EntField("商家 ID")
    private Long merchantId;

    @EntField("报价")
    private BigDecimal price;

    @EntField("版本号")
    private Long version;
}
