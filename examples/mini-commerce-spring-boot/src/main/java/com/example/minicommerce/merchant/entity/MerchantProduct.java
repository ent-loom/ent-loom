package com.example.minicommerce.merchant.entity;

import com.example.minicommerce.access.MerchantData;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商家自己的可售商品；商品名称、价格和状态均由商家独立维护。 */
@EntEntity("商家商品")
@Getter
@Setter
@NoArgsConstructor
public class MerchantProduct implements MerchantData {
    @EntField("商品 ID")
    private Long id;

    @EntField("商家 ID")
    private Long merchantId;

    @EntField("商品名称")
    private String name;

    @EntField("销售价")
    private BigDecimal price;

    @EntField("启用状态")
    private Boolean active;

    @EntField("版本号")
    private Long version;
}
