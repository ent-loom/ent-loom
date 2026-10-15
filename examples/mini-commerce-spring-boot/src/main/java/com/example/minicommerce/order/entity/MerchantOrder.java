package com.example.minicommerce.order.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudField;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.RelationCardinality;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.access.OrderReadPolicy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商家的独立销售订单；客户一次结算可按商家拆成多个销售订单。 */
@EntEntity("商家销售订单")
@EntCrudEntity(httpExposed = false)
@EntDdlIndex(name = "idx_merchant_order_merchant", fields = "merchantId")
@EntDdlIndex(name = "idx_merchant_order_customer", fields = "customerId")
@Getter
@Setter
@NoArgsConstructor
public class MerchantOrder implements OrderReadPolicy {
    @EntField("销售订单 ID")
    private Long id;

    @EntField("客户结算单 ID")
    private Long orderId;

    @EntField("客户 ID")
    private Long customerId;

    @EntField("商家 ID")
    private Long merchantId;

    @EntField("订单状态")
    private OrderStatus status;

    @EntField("订单金额")
    private BigDecimal totalAmount;

    @EntField("创建时间")
    private LocalDateTime createdAt;

    @EntField("客户")
    @EntCrudField(targetClass = Customer.class, sourceField = "customerId")
    private Customer customer;

    @EntField("订单明细")
    @EntCrudField(targetClass = OrderItem.class, targetField = "merchantOrderId",
        cardinality = RelationCardinality.ONE_TO_MANY)
    private List<OrderItem> itemList;
}
