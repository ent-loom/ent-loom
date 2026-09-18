package com.example.minicommerce.product.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.product.entity.Product;
import java.util.Optional;

/** 商品数据访问接口；调用方负责动作授权。 */
@EntDao
public interface ProductDao extends EntityDao<Product, Long> {
    /** 返回下单所需的商品投影，商品状态仍由下单服务校验。 */
    @EntQuery("select id, name, price, active from product where id = :id")
    Optional<Product> findForOrder(Long id);
}
