package com.example.minicommerce.order.service;

import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.exception.OrderValidationException;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.security.OrderAction;
import com.example.minicommerce.product.dao.ProductDao;
import com.example.minicommerce.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 承载下单事务和业务不变量的应用服务。 */
@Service
@RequiredArgsConstructor
public class PlaceOrderService {
    private final CustomerDao customerDao;
    private final ProductDao productDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final OrderAccessPolicy accessPolicy;

    /** 校验客户和商品，锁定当前价格快照，再一次性写入订单聚合。 */
    @Transactional
    public PlaceOrderResult handle(PlaceOrderCommand command) {
        accessPolicy.require(OrderAction.PLACE);
        validateCommand(command);
        Customer customer = customerDao.findById(command.customerId())
            .orElseThrow(() -> new OrderValidationException(OrderError.CUSTOMER_NOT_FOUND));
        Set<Long> productIds = collectProductIds(command.items());
        Map<Long, Product> productsById = productDao.findAllByIdAsMap(productIds);

        List<OrderItem> items = command.items().stream()
            .map(requestItem -> createOrderItem(requestItem, productsById.get(requestItem.productId())))
            .toList();
        BigDecimal totalAmount = items.stream()
            .map(OrderItem::getLineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setCustomerId(customer.getId());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(totalAmount);
        order.setCreatedAt(LocalDateTime.now());
        Long orderId = orderDao.insert(order);
        for (OrderItem item : items) {
            item.setOrderId(orderId);
        }
        orderItemDao.insertAll(items);
        return new PlaceOrderResult(orderId, totalAmount);
    }

    /** 服务入口也执行请求结构校验，避免绕过 Controller 时产生空指针或半成品订单。 */
    private void validateCommand(PlaceOrderCommand command) {
        if (command == null) {
            throw new OrderValidationException(OrderError.REQUEST_REQUIRED);
        }
        if (command.customerId() == null) {
            throw new OrderValidationException(OrderError.CUSTOMER_REQUIRED);
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new OrderValidationException(OrderError.ITEMS_REQUIRED);
        }
        for (PlaceOrderItem item : command.items()) {
            if (item == null) {
                throw new OrderValidationException(OrderError.ITEM_REQUIRED);
            }
            if (item.productId() == null) {
                throw new OrderValidationException(OrderError.PRODUCT_REQUIRED);
            }
            if (item.quantity() == null) {
                throw new OrderValidationException(OrderError.QUANTITY_REQUIRED);
            }
            if (item.quantity() <= 0) {
                throw new OrderValidationException(OrderError.QUANTITY_INVALID);
            }
        }
    }

    private Set<Long> collectProductIds(List<PlaceOrderItem> requestItems) {
        Set<Long> productIds = new LinkedHashSet<>();
        for (PlaceOrderItem requestItem : requestItems) {
            if (!productIds.add(requestItem.productId())) {
                throw new OrderValidationException(OrderError.DUPLICATE_PRODUCT);
            }
        }
        return productIds;
    }

    private OrderItem createOrderItem(PlaceOrderItem requestItem, Product product) {
        if (product == null) {
            throw new OrderValidationException(OrderError.PRODUCT_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new OrderValidationException(OrderError.PRODUCT_INACTIVE);
        }
        if (product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new OrderValidationException(OrderError.PRODUCT_PRICE_INVALID);
        }
        BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(requestItem.quantity()));
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(requestItem.quantity());
        item.setLineAmount(lineAmount);
        return item;
    }
}
