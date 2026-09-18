package com.example.minicommerce.order.service;

import com.example.minicommerce.order.exception.OrderValidationException;
import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.security.OrderAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 订单查询入口，先检查访问权限，再读取订单及明细。 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final CustomerDao customerDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final OrderAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public OrderDetail findDetail(Long id) {
        accessPolicy.require(OrderAction.DETAIL);
        Order order = orderDao.findById(id)
            .orElseThrow(() -> new OrderValidationException("ORDER_NOT_FOUND", "订单不存在"));
        String customerName = customerDao.findById(order.getCustomerId())
            .map(customer -> customer.getDisplayName())
            .orElseThrow(() -> new OrderValidationException("ORDER_NOT_FOUND", "订单不存在"));
        List<OrderDetail.Item> items = orderItemDao.findByOrderId(id).stream()
            .map(this::toDetailItem)
            .toList();
        return new OrderDetail(
            order.getId(),
            order.getCustomerId(),
            customerName,
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            items
        );
    }

    private OrderDetail.Item toDetailItem(OrderItem item) {
        return new OrderDetail.Item(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getLineAmount()
        );
    }
}
