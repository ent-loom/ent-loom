package com.example.minicommerce.merchant.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.merchant.entity.MerchantProduct;

/** 商家商品数据访问接口；商品归属由授权范围保护。 */
@EntDao
public interface MerchantProductDao extends EntityDao<MerchantProduct, Long> {
}
