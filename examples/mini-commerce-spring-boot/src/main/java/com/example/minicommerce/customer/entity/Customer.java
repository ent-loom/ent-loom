package com.example.minicommerce.customer.entity;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    entity = "customer",
    label = "客户",
    description = "商城客户主数据",
    service = "mini-commerce",
    defaultLabelFields = {"displayName"}
)
@EntCrudEntity(name = "customer", table = "customer", ownerService = "mini-commerce")
@Getter
@Setter
@NoArgsConstructor
public class Customer {
    /** 客户主键。 */
    @EntField(value = EntFieldKind.ID, label = "客户 ID", required = OptionalBoolean.TRUE)
    private Long id;

    /** 客户展示名称。 */
    @EntField(value = EntFieldKind.TEXT, label = "客户名称", required = OptionalBoolean.TRUE)
    private String displayName;

    /** 客户联系邮箱。 */
    @EntField(value = EntFieldKind.TEXT, label = "邮箱", required = OptionalBoolean.TRUE)
    private String email;

}
