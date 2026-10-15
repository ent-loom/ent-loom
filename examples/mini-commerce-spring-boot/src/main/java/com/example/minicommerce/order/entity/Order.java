package com.example.minicommerce.order.entity;

import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.RelationCardinality;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.access.OrderAccessPolicy;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一个客户与一个商家的订单；一次跨商家下单创建多张订单。 */
@EntEntity("订单")
@EntCrudEntity(httpExposed = false)
@EntDdlIndex(name = "idx_order_merchant", fields = "merchantId")
@EntDdlIndex(name = "idx_order_customer", fields = "customerId")
@Getter
@Setter
@NoArgsConstructor
public class Order implements OrderAccessPolicy {
    /** 订单主键，由数据库自增生成。 */
    @EntField("订单 ID")
    private Long id;

    @EntField("客户 ID")
    private Long customerId;

    @EntField("商家 ID")
    private Long merchantId;

    @EntField("客户")
    @EntCrudField(targetClass = Customer.class, sourceField = "customerId")
    private Customer customer;

    /** 订单生命周期状态，参见 {@link OrderStatus}。 */
    @EntField("订单状态")
    private OrderStatus status;

    /** 下单时各明细金额的合计。 */
    @EntField("订单总金额")
    private BigDecimal totalAmount;

    /** 订单创建时间，使用应用配置的本地时间。 */
    @EntField("创建时间")
    private LocalDateTime createdAt;

    @EntField("订单明细")
    @EntCrudField(targetClass = OrderItem.class, targetField = "orderId", cardinality = RelationCardinality.ONE_TO_MANY)
    private List<OrderItem> orderItemList;
}
