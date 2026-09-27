package com.example.minicommerce.customer.entity;

import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户主数据实体，交由 ent-loom 通用 CRUD 维护。 */
@EntEntity(
    value = "客户",
    description = "商城客户主数据",
    service = "mini-commerce"
)
@EntDdlEntity
@Getter
@Setter
@NoArgsConstructor
public class Customer {
    @EntField("客户 ID")
    @EntDdlField(generationStrategy = GenerationStrategy.AUTO_INCREMENT)
    private Long id;

    @EntField("客户名称")
    @EntDdlField(length = 128)
    private String displayName;

    @EntField("邮箱")
    @EntDdlField(length = 255)
    private String email;

}
