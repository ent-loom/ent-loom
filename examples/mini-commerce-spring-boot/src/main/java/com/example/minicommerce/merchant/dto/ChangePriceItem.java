package com.example.minicommerce.merchant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** 单条商品改价明细。 */
public record ChangePriceItem(
    @NotNull(message = "商品 ID 不能为空")
    @Positive(message = "商品 ID 须为正整数")
    Long id,

    @NotNull(message = "预期版本不能为空")
    @PositiveOrZero(message = "预期版本不能为负数")
    Long version,

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "价格须大于 0")
    @Digits(integer = 14, fraction = 6, message = "价格最多包含 14 位整数和 6 位小数")
    BigDecimal price
) {
}
