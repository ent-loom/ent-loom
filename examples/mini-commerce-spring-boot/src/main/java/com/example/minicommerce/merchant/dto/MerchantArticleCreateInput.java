package com.example.minicommerce.merchant.dto;

import com.example.minicommerce.merchant.entity.MerchantArticle;

/** 创建只接收文章内容，实体约束由 CRUD 内核统一校验。 */
public record MerchantArticleCreateInput(String title, String content) {
    public MerchantArticle toEntity() {
        var article = new MerchantArticle();
        article.setTitle(title);
        article.setContent(content);
        return article;
    }
}
