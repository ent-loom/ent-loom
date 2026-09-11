package com.example.minicommerce.order.service;

import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.exception.OrderValidationException;
import com.example.minicommerce.order.dto.OrderCustomerInfo;
import com.example.minicommerce.order.dto.OrderProductInfo;
import com.example.minicommerce.order.model.OrderItemSnapshot;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.repository.CustomerRepository;
import com.example.minicommerce.order.repository.ProductRepository;
import com.example.minicommerce.order.repository.OrderRepository;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.security.OrderAction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 承载下单事务和业务不变量的应用服务。 */
@Service
public class PlaceOrderService {
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderAccessPolicy accessPolicy;

    public PlaceOrderService(ProductRepository productRepository,
                             CustomerRepository customerRepository,
                             OrderRepository orderRepository,
                             OrderAccessPolicy accessPolicy) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.accessPolicy = accessPolicy;
    }

    /** 校验客户和商品，锁定当前价格快照，再一次性写入订单聚合。 */
    @Transactional(rollbackFor = Exception.class)
    public PlaceOrderResult handle(PlaceOrderCommand command) {
        accessPolicy.require(OrderAction.PLACE);
        if (command == null || command.getCustomerId() == null) {
            throw new OrderValidationException("CUSTOMER_REQUIRED", "下单客户不能为空");
        }
        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw new OrderValidationException("ITEMS_REQUIRED", "订单至少需要一件商品");
        }

        OrderCustomerInfo customer = customerRepository.findById(command.getCustomerId())
            .orElseThrow(() -> new OrderValidationException("CUSTOMER_NOT_FOUND", "客户不存在"));
        Set<Long> productIds = new HashSet<>();
        List<OrderItemSnapshot> snapshots = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PlaceOrderItem requestItem : command.getItems()) {
            if (requestItem == null || requestItem.getProductId() == null
                || requestItem.getQuantity() == null || requestItem.getQuantity() <= 0) {
                throw new OrderValidationException("ITEM_INVALID", "商品和数量必须有效");
            }
            if (!productIds.add(requestItem.getProductId())) {
                throw new OrderValidationException("DUPLICATE_PRODUCT", "同一订单不能重复提交商品");
            }
            OrderProductInfo product = productRepository.findById(requestItem.getProductId())
                .orElseThrow(() -> new OrderValidationException("PRODUCT_NOT_FOUND", "商品不存在"));
            if (!product.active()) {
                throw new OrderValidationException("PRODUCT_INACTIVE", "商品当前不可下单");
            }
            if (product.price() == null || product.price().signum() <= 0) {
                throw new OrderValidationException("PRODUCT_PRICE_INVALID", "商品价格必须大于零");
            }
            BigDecimal lineAmount = product.price().multiply(BigDecimal.valueOf(requestItem.getQuantity()));
            snapshots.add(new OrderItemSnapshot(
                product.id(), product.name(), product.price(), requestItem.getQuantity(), lineAmount));
            totalAmount = totalAmount.add(lineAmount);
        }

        Long orderId = orderRepository.insertOrder(
            customer.id(), OrderStatus.CREATED, totalAmount, LocalDateTime.now());
        orderRepository.insertItems(orderId, snapshots);
        return new PlaceOrderResult(orderId, totalAmount);
    }
}
