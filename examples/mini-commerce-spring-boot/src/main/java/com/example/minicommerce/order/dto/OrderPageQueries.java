package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** 两端只接受各自业务筛选字段，当前数据归属由框架授权提供。 */
public final class OrderPageQueries {
    private OrderPageQueries() {}

    public record Consumer(@Min(value = 1, message = "页码须从 1 开始") Integer pageNumber,
                           @Min(value = 1, message = "单页至少 1 条")
                           @Max(value = 100, message = "单页最多 100 条") Integer pageSize,
                           @Positive(message = "商家 ID 须为正整数") Long merchantId) {
        public Consumer {
            pageNumber = pageNumber == null ? 1 : pageNumber;
            pageSize = pageSize == null ? 20 : pageSize;
        }
    }

    public record Merchant(@Min(value = 1, message = "页码须从 1 开始") Integer pageNumber,
                           @Min(value = 1, message = "单页至少 1 条")
                           @Max(value = 100, message = "单页最多 100 条") Integer pageSize,
                           @Positive(message = "客户 ID 须为正整数") Long customerId) {
        public Merchant {
            pageNumber = pageNumber == null ? 1 : pageNumber;
            pageSize = pageSize == null ? 20 : pageSize;
        }
    }
}
