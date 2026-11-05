package com.example.minicommerce.customer.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.example.minicommerce.merchant.entity.Merchant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity("客户")
@EntCrudGrant(identities = "merchant", actions = {"view", "create", "update"})
@EntCrudGrant(identities = "customer", actions = {"view", "create", "update"}, scopeField = "id")
@EntCrudEntity(removeActions = "delete", requiredCreateFields = {"displayName", "email"})
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

    @EntField("所属商户 ID")
    @EntRelation(Merchant.class)
    private Long merchantId;

}
