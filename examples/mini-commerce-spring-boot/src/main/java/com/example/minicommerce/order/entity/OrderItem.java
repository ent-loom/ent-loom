package com.example.minicommerce.order.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 订单明细持久化实体，由订单业务服务经 OrderItemDao 保存。 */
@EntEntity(entity = "orderItem", value = "订单明细", description = "商城订单明细",
    service = "mini-commerce", table = "commerce_order_item")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    /** 明细主键，由数据库自增生成。 */
    @EntField("明细 ID")
    private Long id;

    /** 所属订单主键，由订单聚合保存时填充。 */
    @EntField("订单 ID")
    private Long orderId;

    @EntField("商品 ID")
    private Long productId;

    /** 下单时的商品名称快照，不随商品主数据变化。 */
    @EntField("商品名称")
    private String productName;

    /** 下单时的商品单价快照。 */
    @EntField("成交单价")
    private BigDecimal unitPrice;

    /** 购买数量，业务层校验为正数。 */
    @EntField("购买数量")
    private Integer quantity;

    /** 成交单价乘以购买数量，由业务层计算。 */
    @EntField("明细金额")
    private BigDecimal lineAmount;

}
