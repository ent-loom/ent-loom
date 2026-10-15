package com.example.minicommerce.customer.entity;

import com.example.minicommerce.common.access.CustomerReadPolicy;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EntEntity("顾客地址")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress implements CustomerReadPolicy {
    @EntField("地址 ID")
    private Long id;

    @EntField("所属顾客 ID")
    private Long customerId;

    @EntField("收件人")
    private String recipient;

    @EntField("联系电话")
    private String phone;

    @EntField("详细地址")
    private String address;
}
