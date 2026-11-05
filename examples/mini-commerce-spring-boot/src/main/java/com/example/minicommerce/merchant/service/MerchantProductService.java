package com.example.minicommerce.merchant.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.mapper.MerchantProductMapper;
import org.springframework.stereotype.Service;

@Service
public class MerchantProductService extends ServiceImpl<MerchantProductMapper, MerchantProduct> {
}
