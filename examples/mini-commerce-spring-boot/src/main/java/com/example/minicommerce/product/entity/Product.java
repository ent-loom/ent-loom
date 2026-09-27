package com.example.minicommerce.product.entity;

import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商品主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    value = "商品",
    description = "商城商品主数据",
    service = "mini-commerce"
)
@EntDdlEntity
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @EntField("商品 ID")
    @EntDdlField(generationStrategy = GenerationStrategy.AUTO_INCREMENT)
    private Long id;

    @EntField("商品名称")
    @EntDdlField(length = 128)
    private String name;

    /** 当前销售价；下单时会复制为订单明细价格快照。 */
    @EntField("销售价")
    @EntDdlField(precision = 10, scale = 2)
    private BigDecimal price;

    /** 商品是否允许下单。 */
    @EntField("启用状态")
    private Boolean active;

}
