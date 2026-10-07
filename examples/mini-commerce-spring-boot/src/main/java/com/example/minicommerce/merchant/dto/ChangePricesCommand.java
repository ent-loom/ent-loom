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
public record ChangePricesCommand(@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid PriceChange> items) {
    public record PriceChange(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long version,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 6) BigDecimal price) {}
}
