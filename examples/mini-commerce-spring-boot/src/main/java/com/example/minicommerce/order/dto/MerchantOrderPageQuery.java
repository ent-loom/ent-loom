package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** 商家端销售订单分页：仅接受客户筛选，商家归属由框架授权提供。 */
public record MerchantOrderPageQuery(
    @Min(value = 1, message = "页码须从 1 开始") Integer pageNumber,
    @Min(value = 1, message = "单页至少 1 条")
    @Max(value = 100, message = "单页最多 100 条") Integer pageSize,
    @Positive(message = "客户 ID 须为正整数") Long customerId
) {
    public MerchantOrderPageQuery {
        pageNumber = pageNumber == null ? 1 : pageNumber;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
