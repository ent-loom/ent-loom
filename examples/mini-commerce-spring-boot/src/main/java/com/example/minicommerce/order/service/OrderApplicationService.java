package com.example.minicommerce.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.customer.service.CustomerService;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.service.MerchantProductService;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单事务编排；原始 Mapper 的授权范围由业务代码落实。 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final CustomerService customerService;
    private final MerchantProductService merchantProductService;
    private final OrderService orderService;
    private final OrderItemService orderItemService;

    @Transactional
    public PlaceOrderResult place(AuthorizedActionContext access, PlaceOrderCommand command) {
        OrderScope scope = OrderScope.from(access, Order.CREATE);
        if (command == null) throw new BusinessException(OrderError.REQUEST_REQUIRED);
        if (command.items() == null || command.items().isEmpty()) throw new BusinessException(OrderError.ITEMS_REQUIRED);
        Set<Long> ids = new LinkedHashSet<>();
        for (PlaceOrderItem item : command.items()) {
            if (item == null) throw new BusinessException(OrderError.ITEM_REQUIRED);
            if (item.merchantProductId() == null) throw new BusinessException(OrderError.MERCHANT_PRODUCT_REQUIRED);
            if (item.quantity() == null) throw new BusinessException(OrderError.QUANTITY_REQUIRED);
            if (item.quantity() <= 0) throw new BusinessException(OrderError.QUANTITY_INVALID);
            if (!ids.add(item.merchantProductId())) throw new BusinessException(OrderError.DUPLICATE_MERCHANT_PRODUCT);
        }
        Long customerId = Long.valueOf(access.visitor().requireIdentity("customer").getIdentityId());
        if (customerService.getById(customerId) == null) throw new BusinessException(OrderError.CUSTOMER_NOT_FOUND);
        Map<Long, MerchantProduct> productMap = merchantProductService.listByIds(ids).stream()
            .collect(Collectors.toMap(MerchantProduct::getId, product -> product));
        Map<Long, List<OrderItem>> groups = new LinkedHashMap<>();
        for (PlaceOrderItem request : command.items()) {
            MerchantProduct product = productMap.get(request.merchantProductId());
            if (product == null) throw new BusinessException(OrderError.MERCHANT_PRODUCT_NOT_FOUND);
            if (!Boolean.TRUE.equals(product.getActive())) throw new BusinessException(OrderError.MERCHANT_PRODUCT_INACTIVE);
            if (product.getPrice() == null || product.getPrice().signum() <= 0) throw new BusinessException(OrderError.MERCHANT_PRODUCT_PRICE_INVALID);
            OrderItem item = new OrderItem();
            item.setMerchantProductId(product.getId());
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(request.quantity());
            item.setLineAmount(product.getPrice().multiply(BigDecimal.valueOf(request.quantity())));
            groups.computeIfAbsent(product.getMerchantId(), ignored -> new ArrayList<>()).add(item);
        }
        List<Long> orderIds = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime createdAt = LocalDateTime.now();
        for (var group : groups.entrySet()) {
            Order order = new Order();
            order.setCustomerId(customerId);
            order.setMerchantId(group.getKey());
            order.setStatus(OrderStatus.CREATED);
            order.setTotalAmount(group.getValue().stream().map(OrderItem::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            order.setCreatedAt(createdAt);
            scope.requireInsert(order);
            if (!orderService.save(order) || order.getId() == null) throw new IllegalStateException("订单创建失败");
            orderIds.add(order.getId());
            total = total.add(order.getTotalAmount());
            for (OrderItem item : group.getValue()) {
                item.setOrderId(order.getId());
                if (!orderItemService.save(item)) throw new IllegalStateException("订单明细创建失败");
            }
        }
        return new PlaceOrderResult(orderIds, total);
    }

    @Transactional
    public Long cancel(AuthorizedActionContext access, Long id) {
        OrderScope scope = OrderScope.from(access, Order.CANCEL);
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>().eq(Order::getId, id);
        scope.apply(query);
        Order order = orderService.getOne(query);
        if (order == null) throw new BusinessException(OrderError.ORDER_NOT_FOUND);
        if (order.getStatus() != OrderStatus.CREATED) throw new BusinessException(OrderError.ORDER_NOT_CANCELLABLE);
        LambdaUpdateWrapper<Order> update = new LambdaUpdateWrapper<Order>()
            .eq(Order::getId, id).eq(Order::getStatus, OrderStatus.CREATED).set(Order::getStatus, OrderStatus.CANCELLED);
        scope.apply(update);
        if (!orderService.update(update)) throw new EntityDaoWriteMissException("目标不存在或不可写: Order");
        return id;
    }
}
