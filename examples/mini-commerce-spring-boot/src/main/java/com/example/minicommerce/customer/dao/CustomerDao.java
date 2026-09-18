package com.example.minicommerce.customer.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.customer.entity.Customer;

/** 客户数据访问接口；调用方负责动作授权。 */
@EntDao
public interface CustomerDao extends EntityDao<Customer, Long> {
}
