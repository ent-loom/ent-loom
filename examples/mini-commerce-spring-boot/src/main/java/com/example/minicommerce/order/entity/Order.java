package com.example.minicommerce.order.entity;

import com.entloom.crud.annotations.EntCrudField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.RelationCardinality;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 每笔订单属于一个客户和一个商家；跨商家下单由 Handler 在同一事务内拆分。
 *
 * <p>标准读取复用客户、商家策略；下单经过显式业务动作的场景授权。</p>
 */
@EntEntity("订单")
@Getter
@Setter
@NoArgsConstructor
public class Order {
    public static final String PLACE = "place";

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
