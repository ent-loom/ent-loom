package com.example.minicommerce.merchant.dto;

import com.example.minicommerce.merchant.entity.MerchantArticle;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** 创建只接收文章内容，实体约束由 CRUD 内核统一校验。 */
public record CreateMerchantArticle(String title, String content) {
    public MerchantArticle toEntity() {
        var article = new MerchantArticle();
        article.setTitle(title);
        article.setContent(content);
        return article;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不支持的创建字段: " + name);
    }
}
