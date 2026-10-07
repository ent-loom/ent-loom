package com.example.minicommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** 两端只接受各自业务筛选字段，当前数据归属由框架授权提供。 */
public final class OrderPageQueries {
    private OrderPageQueries() {}

    public record Consumer(@Min(1) Integer pageNumber, @Min(1) @Max(100) Integer pageSize,
                           @Positive Long merchantId) {
        public Consumer {
            pageNumber = pageNumber == null ? 1 : pageNumber;
            pageSize = pageSize == null ? 20 : pageSize;
        }
    }

    public record Merchant(@Min(1) Integer pageNumber, @Min(1) @Max(100) Integer pageSize,
                           @Positive Long customerId) {
        public Merchant {
            pageNumber = pageNumber == null ? 1 : pageNumber;
            pageSize = pageSize == null ? 20 : pageSize;
        }
    }
}
