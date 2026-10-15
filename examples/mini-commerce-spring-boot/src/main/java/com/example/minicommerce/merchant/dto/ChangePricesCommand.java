package com.example.minicommerce.merchant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 显式改价合同，不接受商家归属或商品关联字段。 */
public record ChangePricesCommand(
    @NotNull(message = "改价商品列表不能为空") @Size(min = 1, max = 100, message = "每批须包含 1～100 条商品")
    List<@NotNull(message = "改价商品不能为空") @Valid PriceChange> items) {
    public record PriceChange(
        @NotNull(message = "商品 ID 不能为空") @Positive(message = "商品 ID 须为正整数") Long id,
        @NotNull(message = "预期版本不能为空") @PositiveOrZero(message = "预期版本不能为负数") Long version,
        @NotNull(message = "价格不能为空") @DecimalMin(value = "0", inclusive = false, message = "价格须大于 0")
        @Digits(integer = 14, fraction = 6, message = "价格最多包含 14 位整数和 6 位小数") BigDecimal price) {}
}
