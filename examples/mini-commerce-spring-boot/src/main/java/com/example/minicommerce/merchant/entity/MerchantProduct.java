package com.example.minicommerce.merchant.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.crud.annotations.EntCrudGrantScope;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 商家自己的可售商品；商品名称、价格和状态均由商家独立维护。 */
@EntEntity("商家商品")
@EntCrudGrant(identities = "merchant", actions = {"view", "create", "update", "delete", "batch-update"})
@EntCrudGrant(identities = {"public", "customer"}, actions = "view", scope = EntCrudGrantScope.EQUAL,
    scopeField = "active", scopeValue = "true", inheritFields = false,
    fields = @EntCrudGrant.Fields({"id", "merchantId", "name", "price"}))
@EntCrudEntity(
    addActions = "batch-update",
    fieldLimits = {
        "RETURN:id,merchantId,name,price,active,version",
        "FILTER:active",
        "SORT:id",
        "WRITE:name,price,active"
    }
)
@Getter
@Setter
@NoArgsConstructor
public class MerchantProduct {
    @EntField("商品 ID")
    @Positive(message = "商品 ID 须为正整数")
    private Long id;

    @EntField("商家 ID")
    @EntRelation(targetEntity = "merchant")
    private Long merchantId;

    @EntField("商品名称")
    @NotNull(message = "商品名称不能为空")
    private String name;

    @EntField("销售价")
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "价格须大于 0")
    @Digits(integer = 14, fraction = 6, message = "价格最多包含 14 位整数和 6 位小数")
    private BigDecimal price;

    @EntField("启用状态")
    @NotNull(message = "启用状态不能为空")
    private Boolean active;

    @EntField("版本号")
    @PositiveOrZero(message = "预期版本不能为负数")
    private Long version;
}
