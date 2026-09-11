package com.example.minicommerce.customer.entity;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.enums.EntFieldKind;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    entity = "customer",
    label = "客户",
    description = "商城客户主数据",
    service = "mini-commerce",
    defaultLabelFields = {"displayName"}
)
@EntCrudEntity(name = "customer", table = "customer", ownerService = "mini-commerce")
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
