package com.example.minicommerce.order.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.order.entity.MerchantOrder;

/** 商家销售订单数据访问接口。 */
@EntDao
public interface MerchantOrderDao extends EntityDao<MerchantOrder, Long> {
}
