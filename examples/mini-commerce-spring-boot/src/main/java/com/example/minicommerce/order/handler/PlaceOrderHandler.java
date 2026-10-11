package com.example.minicommerce.order.handler;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.merchant.dao.MerchantProductDao;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.order.dao.MerchantOrderDao;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.MerchantOrder;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.enums.OrderStatus;
import com.example.minicommerce.order.exception.OrderValidationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 客户结算场景：校验商家商品，并在同一事务内按商家创建销售订单。 */
@Component
@RequiredArgsConstructor
@EntCrudCommandAction(
    entityClass = Order.class,
    scene = Order.PLACE
)
public class PlaceOrderHandler implements CommandActionSceneHandler<PlaceOrderCommand, PlaceOrderResult> {
    private final CustomerDao customerDao;
    private final MerchantProductDao merchantProducts;
    private final OrderDao orders;
    private final MerchantOrderDao merchantOrders;
    private final OrderItemDao orderItems;

    @Override
    @Transactional
    public CommandResult<PlaceOrderResult> handle(CommandSpec<PlaceOrderCommand> spec,
        SceneDelegate<CommandSpec<PlaceOrderCommand>, CommandResult<PlaceOrderResult>> delegate) {
        PlaceOrderCommand command = spec.getPayload();
        validateCommand(command);
        Customer customer = customerDao.findById(command.customerId())
            .orElseThrow(() -> new OrderValidationException(OrderError.CUSTOMER_NOT_FOUND));
        Set<Long> merchantProductIds = collectMerchantProductIds(command.items());
        Map<Long, MerchantProduct> merchantProductMap = merchantProducts.findAllByIdAsMap(merchantProductIds);
        Map<Long, List<OrderItem>> itemsByMerchant = new LinkedHashMap<>();
        for (PlaceOrderItem requestItem : command.items()) {
            MerchantProduct product = merchantProductMap.get(requestItem.merchantProductId());
            OrderItem item = createOrderItem(requestItem, product);
            itemsByMerchant.computeIfAbsent(product.getMerchantId(), ignored -> new ArrayList<>()).add(item);
        }

        BigDecimal totalAmount = itemsByMerchant.values().stream().flatMap(List::stream)
            .map(OrderItem::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Order checkout = new Order();
        checkout.setCustomerId(customer.getId());
        checkout.setStatus(OrderStatus.CREATED);
        checkout.setTotalAmount(totalAmount);
        checkout.setCreatedAt(LocalDateTime.now());
        Long checkoutId = orders.insert(checkout);

        List<Long> merchantOrderIds = new ArrayList<>();
        for (Map.Entry<Long, List<OrderItem>> entry : itemsByMerchant.entrySet()) {
            MerchantOrder merchantOrder = new MerchantOrder();
            merchantOrder.setOrderId(checkoutId);
            merchantOrder.setCustomerId(customer.getId());
            merchantOrder.setMerchantId(entry.getKey());
            merchantOrder.setStatus(OrderStatus.CREATED);
            merchantOrder.setTotalAmount(entry.getValue().stream().map(OrderItem::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            merchantOrder.setCreatedAt(checkout.getCreatedAt());
            Long merchantOrderId = merchantOrders.insert(merchantOrder);
            merchantOrderIds.add(merchantOrderId);
            for (OrderItem item : entry.getValue()) {
                item.setOrderId(checkoutId);
                item.setMerchantOrderId(merchantOrderId);
            }
            orderItems.insertAll(entry.getValue());
        }
        return CommandResult.success(new PlaceOrderResult(checkoutId, merchantOrderIds, totalAmount));
    }

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
            if (item.merchantProductId() == null) {
                throw new OrderValidationException(OrderError.MERCHANT_PRODUCT_REQUIRED);
            }
            if (item.quantity() == null) {
                throw new OrderValidationException(OrderError.QUANTITY_REQUIRED);
            }
            if (item.quantity() <= 0) {
                throw new OrderValidationException(OrderError.QUANTITY_INVALID);
            }
        }
    }

    private Set<Long> collectMerchantProductIds(List<PlaceOrderItem> requestItems) {
        Set<Long> merchantProductIds = new LinkedHashSet<>();
        for (PlaceOrderItem requestItem : requestItems) {
            if (!merchantProductIds.add(requestItem.merchantProductId())) {
                throw new OrderValidationException(OrderError.DUPLICATE_MERCHANT_PRODUCT);
            }
        }
        return merchantProductIds;
    }

    private OrderItem createOrderItem(PlaceOrderItem requestItem, MerchantProduct product) {
        if (product == null) {
            throw new OrderValidationException(OrderError.MERCHANT_PRODUCT_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new OrderValidationException(OrderError.MERCHANT_PRODUCT_INACTIVE);
        }
        if (product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new OrderValidationException(OrderError.MERCHANT_PRODUCT_PRICE_INVALID);
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
