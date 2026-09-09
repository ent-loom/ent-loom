package com.example.minicommerce.order.application;

import com.example.minicommerce.order.model.OrderDetail;
import com.example.minicommerce.order.persistence.OrderRepository;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.security.OrderAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单查询入口，先检查访问权限，再读取订单及明细。 */
@Service
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final OrderAccessPolicy accessPolicy;

    public OrderQueryService(OrderRepository orderRepository, OrderAccessPolicy accessPolicy) {
        this.orderRepository = orderRepository;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public OrderDetail findDetail(Long id) {
        accessPolicy.require(OrderAction.DETAIL);
        return orderRepository.findDetail(id)
            .orElseThrow(() -> new OrderValidationException("ORDER_NOT_FOUND", "订单不存在"));
    }
}
