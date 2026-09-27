package com.example.minicommerce.order.entity;

import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.crud.annotations.EntCrudActions;
import com.entloom.crud.annotations.EntCrudAction;
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
 * 订单持久化实体，由下单 Handler 经 OrderDao 保存。
 *
 * <p>订单是流程实体：标准 CRUD 沿用项目全局权限，本实体仅将业务动作限制为下单，
 * 避免其他动作绕过订单流程。</p>
 */
@EntEntity("订单")
@EntCrudActions(@EntCrudAction(value = Order.PLACE, name = "下单", accessEntry = "consumer", capability = "place-order"))
@EntDdlIndex(name = "idx_order_customer", fields = "customerId")
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
