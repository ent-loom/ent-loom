package com.example.minicommerce.merchant.entity;

import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("商户文章")
@Getter
@Setter
@NoArgsConstructor
public class MerchantArticle {
    @EntField("文章 ID")
    private Long id;

    @EntField("所属商户 ID")
    private Long merchantId;

    @EntField("标题")
    private String title;

    @EntField(value = "正文", kind = EntFieldKind.CONTENT)
    private String content;
}
