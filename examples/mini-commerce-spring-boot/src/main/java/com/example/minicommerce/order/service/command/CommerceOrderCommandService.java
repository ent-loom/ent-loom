package com.example.minicommerce.order.service.command;

import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.capability.command.patch.DefaultUpdatePatch;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.customer.service.CustomerService;
import com.example.minicommerce.merchant.entity.MerchantProduct;
import com.example.minicommerce.merchant.service.MerchantProductService;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderItem;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.CommerceOrder;
import com.example.minicommerce.order.entity.CommerceOrderItem;
import com.example.minicommerce.order.enums.CommerceOrderError;
import com.example.minicommerce.order.enums.CommerceOrderStatus;
import com.example.minicommerce.order.service.CommerceOrderItemService;
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

/** 订单命令服务：统一承担下单、取消的业务规则与事务编排；订单访问复用框架授权范围。 */
@Service
@RequiredArgsConstructor
public class CommerceOrderCommandService {
    private static final EntityType<CommerceOrder, Long> ORDER_TYPE = EntityType.of(CommerceOrder.class, Long.class);

    private final CustomerService customerService;
    private final MerchantProductService merchantProductService;
    private final EntityDaoFactory daoFactory;
    private final CommerceOrderItemService commerceOrderItemService;

    @Transactional
    public PlaceOrderResult place(AuthorizedActionContext access, PlaceOrderCommand command) {
        requireAction(access, CommerceOrder.CREATE);
        EntityDao<CommerceOrder, Long> orders = access.dao(daoFactory, ORDER_TYPE);
        Set<Long> productIds = validateItems(command);
        Long customerId = requireCustomer(access);
        Map<Long, List<CommerceOrderItem>> groups = groupItemsByMerchant(command.items(), productIds);
        return createOrders(orders, customerId, groups);
    }

    @Transactional
    public Long cancel(AuthorizedActionContext access, Long id) {
        requireAction(access, CommerceOrder.CANCEL);
        CommerceOrder order = access.dao(daoFactory, ORDER_TYPE).findById(id).orElse(null);
        if (order == null) throw new BusinessException(CommerceOrderError.ORDER_NOT_FOUND);
        if (order.getStatus() != CommerceOrderStatus.CREATED) throw new BusinessException(CommerceOrderError.ORDER_NOT_CANCELLABLE);
        // 状态条件与授权范围共同进入 UPDATE，防止查询后的并发状态变化被覆盖。
        EntityDao<CommerceOrder, Long> cancellableOrders = access.dao(daoFactory, ORDER_TYPE,
            RowConstraint.eq("status", CommerceOrderStatus.CREATED));
        Map<String, Object> values = Map.of("status", CommerceOrderStatus.CANCELLED);
        var patch = new DefaultUpdatePatch<>(CommerceOrder.class, null, id,
            values.keySet(), values.keySet(), values, values, null);
        if (cancellableOrders.updateById(id, patch) != 1) throw new EntityDaoWriteMissException("目标不存在或不可写: CommerceOrder");
        return id;
    }

    private static Set<Long> validateItems(PlaceOrderCommand command) {
        if (command == null) throw new BusinessException(CommerceOrderError.REQUEST_REQUIRED);
        if (command.items() == null || command.items().isEmpty()) throw new BusinessException(CommerceOrderError.ITEMS_REQUIRED);
        Set<Long> ids = new LinkedHashSet<>();
        for (PlaceOrderItem item : command.items()) {
            if (item == null) throw new BusinessException(CommerceOrderError.ITEM_REQUIRED);
            if (item.merchantProductId() == null) throw new BusinessException(CommerceOrderError.MERCHANT_PRODUCT_REQUIRED);
            if (item.quantity() == null) throw new BusinessException(CommerceOrderError.QUANTITY_REQUIRED);
            if (item.quantity() <= 0) throw new BusinessException(CommerceOrderError.QUANTITY_INVALID);
            if (!ids.add(item.merchantProductId())) throw new BusinessException(CommerceOrderError.DUPLICATE_MERCHANT_PRODUCT);
        }
        return ids;
    }

    private Long requireCustomer(AuthorizedActionContext access) {
        Long customerId = Long.valueOf(access.visitor().requireIdentity("customer").getIdentityId());
        if (customerService.getById(customerId) == null) throw new BusinessException(CommerceOrderError.CUSTOMER_NOT_FOUND);
        return customerId;
    }

    /** 使用当前商品信息生成订单明细快照，并按商家拆分。 */
    private Map<Long, List<CommerceOrderItem>> groupItemsByMerchant(List<PlaceOrderItem> requests, Set<Long> productIds) {
        Map<Long, MerchantProduct> productMap = merchantProductService.listByIds(productIds).stream()
            .collect(Collectors.toMap(MerchantProduct::getId, product -> product));
        Map<Long, List<CommerceOrderItem>> groups = new LinkedHashMap<>();
        for (PlaceOrderItem request : requests) {
            MerchantProduct product = productMap.get(request.merchantProductId());
            if (product == null) throw new BusinessException(CommerceOrderError.MERCHANT_PRODUCT_NOT_FOUND);
            if (!Boolean.TRUE.equals(product.getActive())) throw new BusinessException(CommerceOrderError.MERCHANT_PRODUCT_INACTIVE);
            if (product.getPrice() == null || product.getPrice().signum() <= 0) throw new BusinessException(CommerceOrderError.MERCHANT_PRODUCT_PRICE_INVALID);
            CommerceOrderItem item = new CommerceOrderItem();
            item.setMerchantProductId(product.getId());
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(request.quantity());
            item.setLineAmount(product.getPrice().multiply(BigDecimal.valueOf(request.quantity())));
            groups.computeIfAbsent(product.getMerchantId(), ignored -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    private PlaceOrderResult createOrders(EntityDao<CommerceOrder, Long> orders, Long customerId,
        Map<Long, List<CommerceOrderItem>> groups) {
        List<Long> orderIds = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime createdAt = LocalDateTime.now();
        for (var group : groups.entrySet()) {
            CommerceOrder order = new CommerceOrder();
            order.setCustomerId(customerId);
            order.setMerchantId(group.getKey());
            order.setStatus(CommerceOrderStatus.CREATED);
            order.setTotalAmount(group.getValue().stream().map(CommerceOrderItem::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            order.setCreatedAt(createdAt);
            orders.insert(order);
            orderIds.add(order.getId());
            total = total.add(order.getTotalAmount());
            for (CommerceOrderItem item : group.getValue()) {
                item.setOrderId(order.getId());
                if (!commerceOrderItemService.save(item)) throw new IllegalStateException("订单明细创建失败");
            }
        }
        return new PlaceOrderResult(orderIds, total);
    }

    private static void requireAction(AuthorizedActionContext access, String operation) {
        access.requireCurrent();
        if (access.entityType() != CommerceOrder.class
            || !"commerceOrder".equals(access.authorization().getRequest().getOperation().getResourceCode())
            || !operation.equals(access.authorization().getRequest().getOperation().getOperationCode())) {
            throw new PermissionDeniedException("订单动作授权不匹配");
        }
    }
}
