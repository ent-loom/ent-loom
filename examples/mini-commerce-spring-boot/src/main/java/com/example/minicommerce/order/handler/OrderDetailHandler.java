package com.example.minicommerce.order.handler;

import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.annotations.EntCrudQueryHandler;
import com.entloom.crud.core.capability.query.scene.QueryDetailSceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.customer.dao.CustomerDao;
import com.example.minicommerce.order.dao.OrderDao;
import com.example.minicommerce.order.dao.OrderItemDao;
import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.exception.OrderValidationException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 订单详情场景：统一管线授权后，跨实体组装业务 DTO。 */
@Component
@RequiredArgsConstructor
@EntCrudQueryHandler(entityClasses = Order.class, scenes = OrderDetailHandler.SCENE)
public class OrderDetailHandler implements QueryDetailSceneHandler<OrderDetail> {
    public static final String SCENE = "detail";

    private final CustomerDao customerDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;

    @Override
    public Set<CrudRouteKey> routeKeys() {
        return Set.of(new CrudRouteKey(List.of(Order.class.getName()), CrudOperationKey.of(QueryOperation.DETAIL), SCENE));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetail handle(QuerySpec<OrderDetail> spec, SceneDelegate<QuerySpec<OrderDetail>, OrderDetail> delegate) {
        // 专用详情入口只接受单个订单 ID，避免忽略调用方附加的过滤条件。
        var filters = spec.getFilters();
        if (filters.size() != 1 || filters.getFirst() == null || !"id".equals(filters.getFirst().getField())
            || filters.getFirst().getOperator() != FilterOperator.EQ
            || !(filters.getFirst().getValue() instanceof Number idValue)) {
            throw new ValidationException("订单详情需要唯一的数值型 id 等值条件");
        }
        long id = idValue.longValue();
        Order order = orderDao.findById(id)
            .orElseThrow(() -> new OrderValidationException(OrderError.ORDER_NOT_FOUND));
        String customerName = customerDao.findById(order.getCustomerId())
            .map(customer -> customer.getDisplayName())
            .orElseThrow(() -> new OrderValidationException(OrderError.ORDER_NOT_FOUND));
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
