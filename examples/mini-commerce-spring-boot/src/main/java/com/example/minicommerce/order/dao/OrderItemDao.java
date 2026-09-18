package com.example.minicommerce.order.dao;

import com.entloom.crud.annotations.EntQuery;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.starter.dao.EntDao;
import com.example.minicommerce.order.entity.OrderItem;
import java.util.List;

/** 订单明细实体 DAO；明细查询只允许通过所属订单收窄范围。 */
@EntDao
public interface OrderItemDao extends EntityDao<OrderItem, Long> {
    /** 按订单读取明细，并保持数据库中的明细顺序。 */
    @EntQuery("select id, order_id, product_id, product_name, unit_price, quantity, line_amount "
        + "from commerce_order_item where order_id = :orderId order by id")
    List<OrderItem> findByOrderId(Long orderId);
}
