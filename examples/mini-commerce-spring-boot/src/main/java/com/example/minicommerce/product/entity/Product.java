package com.example.minicommerce.product.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @EntField(label = "商品 ID")
    private Long id;

    /** 商品名称。 */
    @EntField(label = "商品名称")
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /** 当前销售价；下单时会复制为订单明细价格快照。 */
    @EntField(label = "销售价")
    @NotNull(message = "销售价不能为空")
    private BigDecimal price;

    /** 商品是否允许下单。 */
    @EntField(label = "启用状态")
    @NotNull(message = "启用状态不能为空")
    private Boolean active;

}
