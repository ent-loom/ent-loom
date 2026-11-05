package com.example.minicommerce.merchant.entity;

import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.annotations.EntCrudGrant;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("商户文章")
@EntCrudGrant(identities = "merchant", actions = {"view", "create", "update", "delete"})
@EntCrudEntity(httpExposed = false)
@Getter
@Setter
@NoArgsConstructor
public class MerchantArticle {
    @EntField("文章 ID")
    private Long id;

    @EntField("所属商户 ID")
    @EntRelation(Merchant.class)
    private Long merchantId;

    @EntField("标题")
    @NotBlank(message = "文章标题不能为空")
    private String title;

    @EntField(value = "正文", kind = EntFieldKind.CONTENT)
    @NotBlank(message = "文章正文不能为空")
    private String content;
}
