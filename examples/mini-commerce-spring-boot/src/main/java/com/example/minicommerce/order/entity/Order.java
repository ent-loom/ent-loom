package com.example.minicommerce.order.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 订单持久化实体，由订单业务服务经 OrderDao 保存。 */
@EntEntity(entity = "order", label = "订单", description = "商城订单",
    service = "mini-commerce")
@EntCrudEntity(name = "order", table = "commerce_order", ownerService = "mini-commerce",
    idPolicy = CrudIdPolicy.GENERATED)
@Getter
@Setter
@NoArgsConstructor
public class Order {
    /** 订单主键，由数据库自增生成。 */
    @EntField("订单 ID")
    private Long id;

    /** 下单客户主键。 */
    @EntField("客户 ID")
    private Long customerId;

    /** 订单生命周期状态，参见 {@link OrderStatus}。 */
    @EntField("订单状态")
    private OrderStatus status;

    /** 下单时各明细金额的合计。 */
    @EntField("订单总金额")
    private BigDecimal totalAmount;

    /** 订单创建时间，使用应用配置的本地时间。 */
    @EntField("创建时间")
    private LocalDateTime createdAt;

}
