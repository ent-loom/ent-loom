package com.example.minicommerce.customer.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 平台用户的银行卡绑定信息；完整卡号由支付机构保管。 */
@EntEntity("顾客银行卡")
@Getter
@Setter
@NoArgsConstructor
public class CustomerBankCard {
    @EntField("银行卡 ID")
    private Long id;

    @EntField("所属顾客 ID")
    @EntRelation(targetEntity = "customer")
    private Long customerId;

    @EntField("银行名称")
    private String bankName;

    @EntField("卡号后四位")
    private String cardLastFour;

    @EntField("支付机构绑定标识")
    private String bindingToken;
}
