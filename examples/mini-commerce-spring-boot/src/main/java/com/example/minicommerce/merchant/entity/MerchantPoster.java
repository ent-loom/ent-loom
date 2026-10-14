package com.example.minicommerce.merchant.entity;

import com.example.minicommerce.merchant.model.MerchantRecord;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.enums.SqlType;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("商户文章")
@Getter
@Setter
@NoArgsConstructor
public class MerchantPoster implements MerchantRecord {
    @EntField("文章 ID")
    private Long id;

    @EntField("所属商户 ID")
    private Long merchantId;

    @EntField("标题")
    private String title;

    @EntField("正文")
    @EntDdlField(sqlType = SqlType.TEXT)
    private String content;
}
