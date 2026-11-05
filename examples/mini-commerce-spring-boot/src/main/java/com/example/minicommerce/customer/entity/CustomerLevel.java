package com.example.minicommerce.customer.entity;

import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntRelation;
import com.example.minicommerce.customer.enums.CustomerLevelType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("用户等级信息")
@Getter
@Setter
@NoArgsConstructor
public class CustomerLevel {
    @EntField("等级信息 ID")
    private Long id;

    @EntField("所属商家 ID")
    @EntRelation(targetEntity = "merchant")
    private Long merchantId;

    @EntField("所属顾客 ID")
    @EntRelation(targetEntity = "customer")
    private Long customerId;

    /** 商家维护，参见 {@link CustomerLevelType}。 */
    @EntField("会员等级")
    private CustomerLevelType level;
}
