package com.example.minicommerce.order.entity;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;
import java.math.BigDecimal;

/** 订单明细持久化实体，由订单业务服务经专用 Repository 保存。 */
@EntEntity(entity = "orderItem", label = "订单明细", description = "商城订单明细",
    service = "mini-commerce", defaultLabelFields = {"id"})
@EntCrudEntity(name = "orderItem", table = "commerce_order_item", ownerService = "mini-commerce",
    idPolicy = CrudIdPolicy.GENERATED)
public class OrderItem {
    /** 明细主键，由数据库自增生成。 */
    @EntField(value = EntFieldKind.ID, label = "明细 ID", required = OptionalBoolean.TRUE)
    private Long id;

    /** 所属订单主键，由订单聚合保存时填充。 */
    @EntField(value = EntFieldKind.REF_ID, label = "订单 ID", required = OptionalBoolean.TRUE)
    private Long orderId;

    /** 下单商品主键。 */
    @EntField(value = EntFieldKind.REF_ID, label = "商品 ID", required = OptionalBoolean.TRUE)
    private Long productId;

    /** 下单时的商品名称快照，不随商品主数据变化。 */
    @EntField(value = EntFieldKind.TEXT, label = "商品名称", required = OptionalBoolean.TRUE)
    private String productName;

    /** 下单时的商品单价快照。 */
    @EntField(value = EntFieldKind.NUMBER, label = "成交单价", required = OptionalBoolean.TRUE)
    private BigDecimal unitPrice;

    /** 购买数量，业务层校验为正数。 */
    @EntField(value = EntFieldKind.NUMBER, label = "购买数量", required = OptionalBoolean.TRUE)
    private Integer quantity;

    /** 成交单价乘以购买数量，由业务层计算。 */
    @EntField(value = EntFieldKind.NUMBER, label = "明细金额", required = OptionalBoolean.TRUE)
    private BigDecimal lineAmount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLineAmount() {
        return lineAmount;
    }

    public void setLineAmount(BigDecimal lineAmount) {
        this.lineAmount = lineAmount;
    }
}
