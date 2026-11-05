package com.example.minicommerce.order.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.crud.annotations.EntCrudEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 订单明细由下单事务保存，商家归属通过所属订单确定。 */
@EntEntity("订单明细")
@EntDdlEntity
@EntDdlIndex(name = "idx_order_item_order", fields = {"orderId"})
@EntCrudEntity(httpExposed = false)
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    /** 明细主键，由数据库自增生成。 */
    @EntField("明细 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 由下单 Handler 保存订单后显式填充。 */
    @EntField("订单 ID")
    @EntRelation(targetEntity = "order")
    private Long orderId;

    @EntField("商家商品 ID")
    @EntRelation(targetEntity = "merchantProduct")
    private Long merchantProductId;

    /** 下单时的商品名称快照，不随商家商品变化。 */
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
