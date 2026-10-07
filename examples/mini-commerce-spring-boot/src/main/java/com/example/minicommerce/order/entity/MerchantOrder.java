package com.example.minicommerce.order.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按商家拆分的子订单；本轮以种子数据演示两端分页，暂未接入自动拆单。 */
@EntEntity("商家子订单")
@EntCrudEntity(httpExposed = false, scopeFields = {"customerId", "merchantId"})
@Getter
@Setter
@NoArgsConstructor
public class MerchantOrder {
    @EntField("子订单 ID")
    private Long id;

    @EntField("主订单 ID")
    private Long orderId;

    @EntField("客户 ID")
    private Long customerId;

    @EntField("商家 ID")
    private Long merchantId;

    @EntField("子订单金额")
    private BigDecimal totalAmount;
}
