package com.example.minicommerce.order.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 提交订单的业务命令，不映射为通用 CRUD CREATE。 */
@Getter
@Setter
@NoArgsConstructor
public class PlaceOrderCommand {
    /** 下单客户主键。 */
    private Long customerId;

    /** 订单商品行。 */
    private List<PlaceOrderItem> items;

}
