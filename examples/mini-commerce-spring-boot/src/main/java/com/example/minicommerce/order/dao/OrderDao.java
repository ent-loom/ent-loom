package com.example.minicommerce.order.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.order.entity.Order;

/** 订单实体 DAO；订单聚合的事务编排由应用服务负责。 */
@EntDao
public interface OrderDao extends EntityDao<Order, Long> {
}
