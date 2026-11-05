package com.example.minicommerce.merchant.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudAccess;
import com.entloom.crud.annotations.EntCrudAccessField;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("商家")
@EntCrudEntity(ownershipSpecified = true, ownership = {}, relations = {"merchant=id"})
@EntCrudAccess(identity = "customer", actions = {"view"}, scope = "all", fields = {
    @EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = {"id", "name"}),
    @EntCrudAccessField(use = "EXPAND", mode = "NONE")
})
@EntCrudAccess(identity = "public", actions = {"view"}, scope = "all", fields = {@EntCrudAccessField(use = "RETURN", mode = "INCLUDE", fields = {"id", "name"})})
@EntCrudAccess(identity = "merchant", actions = {"view", "update"}, fields = {@EntCrudAccessField(use = "WRITE", mode = "INCLUDE", fields = {"name", "legalRepresentativeName", "legalRepresentativePhone"})})
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
