package com.example.minicommerce.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 下单请求中的商品行。 */
@Getter
@Setter
@NoArgsConstructor
public class PlaceOrderItem {
    /** 商品主键。 */
    private Long productId;

    /** 购买数量。 */
    private Integer quantity;

}
