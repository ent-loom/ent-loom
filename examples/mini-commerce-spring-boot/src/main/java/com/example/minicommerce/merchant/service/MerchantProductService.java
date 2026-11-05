package com.example.minicommerce.merchant.service;

import com.example.minicommerce.merchant.service.query.ProductSummaryService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.mapper.MerchantProductMapper;
import org.springframework.stereotype.Service;

@Service
public class MerchantProductService extends ServiceImpl<MerchantProductMapper, MerchantProduct> {
    public ProductSummaryService.Page pageAll(Boolean active, int page, int limit) {
        return page(null, active, page, limit);
    }

    public ProductSummaryService.Page pageMerchant(long merchantId, Boolean active, int page, int limit) {
        return page(merchantId, active, page, limit);
    }

    private ProductSummaryService.Page page(Long merchantId, Boolean active, int page, int limit) {
        long total = baseMapper.countSummary(merchantId, active);
        var rows = baseMapper.selectSummary(merchantId, active, limit, ((long) page - 1) * limit);
        return new ProductSummaryService.Page(rows, page, limit, total);
    }
}
