package com.example.minicommerce.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.service.ProductSummaryService;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MerchantProductMapper extends BaseMapper<MerchantProduct> {
    long countSummary(@Param("merchantId") Long merchantId, @Param("active") Boolean active);

    List<ProductSummaryService.Summary> selectSummary(@Param("merchantId") Long merchantId,
        @Param("active") Boolean active, @Param("limit") int limit, @Param("offset") long offset);
}
