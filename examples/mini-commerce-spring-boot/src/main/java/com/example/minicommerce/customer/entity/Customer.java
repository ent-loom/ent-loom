package com.example.minicommerce.customer.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    entity = "customer",
    label = "客户",
    description = "商城客户主数据",
    service = "mini-commerce"
)
// 主数据由请求指定 ID，与示例表的非自增主键保持一致。
@EntCrudEntity(idPolicy = CrudIdPolicy.EXPLICIT)
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
