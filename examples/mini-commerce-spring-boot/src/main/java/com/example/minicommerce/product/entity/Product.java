package com.example.minicommerce.product.entity;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商品主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    entity = "product",
    label = "商品",
    description = "商城商品主数据",
    service = "mini-commerce",
    defaultLabelFields = {"name"}
)
@EntCrudEntity(name = "product", table = "product", ownerService = "mini-commerce")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    /** 商品主键。 */
    @EntField(value = EntFieldKind.ID, label = "商品 ID", required = OptionalBoolean.TRUE)
    private Long id;

    /** 商品名称。 */
    @EntField(value = EntFieldKind.TEXT, label = "商品名称", required = OptionalBoolean.TRUE)
    private String name;

    /** 当前销售价；下单时会复制为订单明细价格快照。 */
    @EntField(value = EntFieldKind.NUMBER, label = "销售价", required = OptionalBoolean.TRUE)
    private BigDecimal price;

    /** 商品是否允许下单。 */
    @EntField(value = EntFieldKind.FLAG, label = "启用状态", required = OptionalBoolean.TRUE)
    private Boolean active;

}
