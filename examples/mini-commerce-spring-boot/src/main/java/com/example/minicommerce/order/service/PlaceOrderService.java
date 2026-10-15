package com.example.minicommerce.order.service;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationProvider;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.BusinessAuthorizationService;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客户下单：使用可信客户归属，在同一事务内按商家创建订单和成交快照。 */
@Service
@RequiredArgsConstructor
public class PlaceOrderService {
    private final BusinessAuthorizationService authorization;
    private final EntityDaoAuthorizationProvider authorizedDaos;
    private final OrderRepository repository;

    @Transactional
    public PlaceOrderResult place(VisitorContext visitor, PlaceOrderCommand command) {
        Long customerId = Long.valueOf(visitor.requireIdentity("customer").getIdentityId());
        BusinessOperation operation = new BusinessOperation("order", "place");
        AuthorizationRequest request = new AuthorizationRequest(visitor, operation, null);
        authorization.authorize(request).requireApplicable(request);
        validateCommand(command);
        if (!repository.customerExists(customerId)) {
            throw new BusinessException(OrderError.CUSTOMER_NOT_FOUND);
        }
        Set<Long> merchantProductIds = collectMerchantProductIds(command.items());
        Map<Long, MerchantProduct> merchantProductMap = repository.findProducts(merchantProductIds).stream()
            .collect(java.util.stream.Collectors.toMap(MerchantProduct::getId, product -> product));
        Map<Long, List<OrderItem>> itemsByMerchant = new LinkedHashMap<>();
        for (PlaceOrderItem requestItem : command.items()) {
            MerchantProduct product = merchantProductMap.get(requestItem.merchantProductId());
            OrderItem item = createOrderItem(requestItem, product);
            itemsByMerchant.computeIfAbsent(product.getMerchantId(), ignored -> new ArrayList<>()).add(item);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDateTime createdAt = LocalDateTime.now();
        List<Long> orderIds = new ArrayList<>();
        for (Map.Entry<Long, List<OrderItem>> entry : itemsByMerchant.entrySet()) {
            Order order = new Order();
            order.setCustomerId(customerId);
            order.setMerchantId(entry.getKey());
            order.setStatus(OrderStatus.CREATED);
            order.setTotalAmount(entry.getValue().stream().map(OrderItem::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            order.setCreatedAt(createdAt);
            Long orderId = repository.insert(order);
            // 新订单须落入本次下单授权范围；任一订单被拒绝时整体回滚。
            authorizedDaos.open(EntityType.of(Order.class, Long.class), visitor, operation, orderId.toString())
                .findById(orderId).orElseThrow(() -> new PermissionDeniedException("订单不在下单授权范围内"));
            orderIds.add(orderId);
            totalAmount = totalAmount.add(order.getTotalAmount());
            for (OrderItem item : entry.getValue()) {
                item.setOrderId(orderId);
            }
            repository.insertItems(entry.getValue());
        }
        return new PlaceOrderResult(orderIds, totalAmount);
    }

    private void validateCommand(PlaceOrderCommand command) {
        if (command == null) {
            throw new BusinessException(OrderError.REQUEST_REQUIRED);
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException(OrderError.ITEMS_REQUIRED);
        }
        for (PlaceOrderItem item : command.items()) {
            if (item == null) {
                throw new BusinessException(OrderError.ITEM_REQUIRED);
            }
            if (item.merchantProductId() == null) {
                throw new BusinessException(OrderError.MERCHANT_PRODUCT_REQUIRED);
            }
            if (item.quantity() == null) {
                throw new BusinessException(OrderError.QUANTITY_REQUIRED);
            }
            if (item.quantity() <= 0) {
                throw new BusinessException(OrderError.QUANTITY_INVALID);
            }
        }
    }

    private Set<Long> collectMerchantProductIds(List<PlaceOrderItem> requestItems) {
        Set<Long> merchantProductIds = new LinkedHashSet<>();
        for (PlaceOrderItem requestItem : requestItems) {
            if (!merchantProductIds.add(requestItem.merchantProductId())) {
                throw new BusinessException(OrderError.DUPLICATE_MERCHANT_PRODUCT);
            }
        }
        return merchantProductIds;
    }

    private OrderItem createOrderItem(PlaceOrderItem requestItem, MerchantProduct product) {
        if (product == null) {
            throw new BusinessException(OrderError.MERCHANT_PRODUCT_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BusinessException(OrderError.MERCHANT_PRODUCT_INACTIVE);
        }
        if (product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new BusinessException(OrderError.MERCHANT_PRODUCT_PRICE_INVALID);
        }
        BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(requestItem.quantity()));
        OrderItem item = new OrderItem();
        item.setMerchantProductId(product.getId());
        item.setMerchantId(product.getMerchantId());
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(requestItem.quantity());
        item.setLineAmount(lineAmount);
        return item;
    }
}
