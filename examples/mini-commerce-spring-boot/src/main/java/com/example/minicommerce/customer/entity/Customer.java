package com.example.minicommerce.customer.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    entity = "customer",
    value = "客户",
    description = "商城客户主数据",
    service = "mini-commerce"
)
@Getter
@Setter
@NoArgsConstructor
public class Customer {
    @EntField("客户 ID")
    private Long id;

    @EntField("客户名称")
    private String displayName;

    @EntField("邮箱")
    private String email;

}
