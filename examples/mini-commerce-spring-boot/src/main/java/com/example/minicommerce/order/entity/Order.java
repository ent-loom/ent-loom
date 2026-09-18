package com.example.minicommerce.order.entity;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 订单持久化实体，由订单业务服务经专用 Repository 保存。 */
@EntEntity(entity = "order", label = "订单", description = "商城订单",
    service = "mini-commerce", defaultLabelFields = {"id"})
@EntCrudEntity(name = "order", table = "commerce_order", ownerService = "mini-commerce",
    idPolicy = CrudIdPolicy.GENERATED)
@Getter
@Setter
@NoArgsConstructor
public class Order {
    /** 订单主键，由数据库自增生成。 */
    @EntField(value = EntFieldKind.ID, label = "订单 ID", required = OptionalBoolean.TRUE)
    private Long id;

    /** 下单客户主键。 */
    @EntField(value = EntFieldKind.REF_ID, label = "客户 ID", required = OptionalBoolean.TRUE)
    private Long customerId;

    /** 订单生命周期状态，参见 {@link OrderStatus}。 */
    @EntField(value = EntFieldKind.ENUM, label = "订单状态", required = OptionalBoolean.TRUE)
    private OrderStatus status;

    /** 下单时各明细金额的合计。 */
    @EntField(value = EntFieldKind.NUMBER, label = "订单总金额", required = OptionalBoolean.TRUE)
    private BigDecimal totalAmount;

    /** 订单创建时间，使用应用配置的本地时间。 */
    @EntField(value = EntFieldKind.DATETIME, label = "创建时间", required = OptionalBoolean.TRUE)
    private LocalDateTime createdAt;

}
