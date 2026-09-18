package com.example.minicommerce.order.service;

import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.product.dao.ProductDao;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.exception.OrderValidationException;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.repository.OrderRepository;
import com.example.minicommerce.order.security.OrderAccessPolicy;
import com.example.minicommerce.order.security.OrderAction;
import com.example.minicommerce.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private final OrderRepository orderRepository;
    private final OrderAccessPolicy accessPolicy;

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

        Customer customer = customerDao.findById(command.getCustomerId())
            .orElseThrow(() -> new OrderValidationException("CUSTOMER_NOT_FOUND", "客户不存在"));
        Set<Long> productIds = new HashSet<>();
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PlaceOrderItem requestItem : command.getItems()) {
            if (requestItem == null || requestItem.getProductId() == null
                || requestItem.getQuantity() == null || requestItem.getQuantity() <= 0) {
                throw new OrderValidationException("ITEM_INVALID", "商品和数量必须有效");
            }
            if (!productIds.add(requestItem.getProductId())) {
                throw new OrderValidationException("DUPLICATE_PRODUCT", "同一订单不能重复提交商品");
            }
            Product product = productDao.findById(requestItem.getProductId())
                .orElseThrow(() -> new OrderValidationException("PRODUCT_NOT_FOUND", "商品不存在"));
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new OrderValidationException("PRODUCT_INACTIVE", "商品当前不可下单");
            }
            if (product.getPrice() == null || product.getPrice().signum() <= 0) {
                throw new OrderValidationException("PRODUCT_PRICE_INVALID", "商品价格必须大于零");
            }
            BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(requestItem.getQuantity()));
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(requestItem.getQuantity());
            item.setLineAmount(lineAmount);
            items.add(item);
            totalAmount = totalAmount.add(lineAmount);
        }

        Order order = new Order();
        order.setCustomerId(customer.getId());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(totalAmount);
        order.setCreatedAt(LocalDateTime.now());
        Long orderId = orderRepository.insert(order, items);
        return new PlaceOrderResult(orderId, totalAmount);
    }
}
