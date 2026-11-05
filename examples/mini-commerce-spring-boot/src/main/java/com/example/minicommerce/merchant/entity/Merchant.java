package com.example.minicommerce.merchant.entity;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.crud.annotations.EntCrudGrantScope;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("商家")
@EntCrudGrant(identities = "merchant", actions = {"view", "create", "update", "delete"}, scopeField = "id")
@EntCrudGrant(identities = {"public", "customer"}, actions = "view", scope = EntCrudGrantScope.ALL,
    inheritFields = false, fields = @EntCrudGrant.Fields({"id", "name"}))
@EntCrudEntity
@Getter
@Setter
@NoArgsConstructor
public class Merchant {
    @EntField("商家 ID")
    private Long id;

    @EntField("商家名称")
    private String name;

    @EntField("法定代表人姓名")
    private String legalRepresentativeName;

    @EntField("法定代表人联系电话")
    private String legalRepresentativePhone;
}
