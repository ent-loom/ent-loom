package com.example.minicommerce.product.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.product.entity.Product;

/** 商品数据访问接口；调用方负责动作授权。 */
@EntDao
public interface ProductDao extends EntityDao<Product, Long> {
}
