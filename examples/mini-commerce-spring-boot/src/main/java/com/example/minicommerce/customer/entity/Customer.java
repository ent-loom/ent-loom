package com.example.minicommerce.customer.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudAccess;
import com.entloom.crud.annotations.EntCrudAccessField;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity("客户")
@EntCrudEntity(actions = {"view", "create", "update"}, ownership = {"merchant"}, relations = {"customer=id"}, requiredCreateFields = {"displayName", "email"})
@EntCrudAccess(identity = "super-admin", fields = {@EntCrudAccessField(use = "WRITE", mode = "ALL")})
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
    @EntRelation(targetEntity = "merchant")
    private Long merchantId;

}
