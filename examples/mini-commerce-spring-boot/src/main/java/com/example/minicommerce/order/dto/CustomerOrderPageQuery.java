package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** 客户端销售订单分页：仅接受商家筛选，客户归属由框架授权提供。 */
public record CustomerOrderPageQuery(
    @Min(value = 1, message = "页码须从 1 开始") Integer pageNumber,
    @Min(value = 1, message = "单页至少 1 条")
    @Max(value = 100, message = "单页最多 100 条") Integer pageSize,
    @Positive(message = "商家 ID 须为正整数") Long merchantId
) {
    public CustomerOrderPageQuery {
        pageNumber = pageNumber == null ? 1 : pageNumber;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
