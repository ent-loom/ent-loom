package com.example.minicommerce.merchant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 批量修改商品价格命令。 */
public record ChangePricesCommand(
    @NotNull(message = "改价商品列表不能为空")
    @Size(min = 1, max = 100, message = "每批须包含 1～100 条商品")
    @Valid
    List<@NotNull(message = "改价商品不能为空") ChangePriceItem> items
) {
}
