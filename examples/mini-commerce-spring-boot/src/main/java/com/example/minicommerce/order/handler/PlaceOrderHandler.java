package com.example.minicommerce.order.handler;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
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
import com.example.minicommerce.product.dao.ProductDao;
import com.example.minicommerce.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 下单场景：统一管线授权后，在一个事务内保存订单与价格快照。 */
@Component
@RequiredArgsConstructor
@EntCrudCommandAction(
    entityClass = Order.class,
    scene = PlaceOrderHandler.SCENE,
    requestType = PlaceOrderCommand.class,
    responseType = PlaceOrderResult.class
)
public class PlaceOrderHandler implements CommandActionSceneHandler<PlaceOrderCommand, PlaceOrderResult> {
    public static final String SCENE = "place";

    private final CustomerDao customerDao;
    private final ProductDao productDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;

    @Override
    public Set<CrudRouteKey> routeKeys() {
        return Set.of(new CrudRouteKey(List.of(Order.class.getName()), CrudOperationKey.of(CommandOperation.ACTION), SCENE));
    }

    @Override
    public CommandActionContract contract() {
        return new CommandActionContract(PlaceOrderCommand.class, PlaceOrderResult.class);
    }

    /** 校验客户和商品，保存当前价格快照，再一次性写入订单聚合。 */
    @Override
    @Transactional
    public CommandResult<PlaceOrderResult> handle(CommandSpec<PlaceOrderCommand> spec,
        SceneDelegate<CommandSpec<PlaceOrderCommand>, CommandResult<PlaceOrderResult>> delegate) {
        PlaceOrderCommand command = spec.getPayload();
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
        return CommandResult.success(new PlaceOrderResult(orderId, totalAmount));
    }

    /** Handler 也执行请求结构校验，避免绕过 Controller 时产生空指针或半成品订单。 */
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
