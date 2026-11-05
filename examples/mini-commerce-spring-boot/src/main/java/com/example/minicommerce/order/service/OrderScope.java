package com.example.minicommerce.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.example.minicommerce.order.entity.Order;
import java.util.Objects;

/** 仅接受本示例能够完整落实的订单授权范围。 */
final class OrderScope {
    private final String field;
    private final String value;

    private OrderScope(String field, String value) { this.field = field; this.value = value; }

    static OrderScope from(AuthorizedActionContext access, String operation) {
        access.requireCurrent();
        if (access.entityType() != Order.class
            || !"order".equals(access.authorization().getRequest().getOperation().getResourceCode())
            || !operation.equals(access.authorization().getRequest().getOperation().getOperationCode())) {
            throw new PermissionDeniedException("订单动作授权不匹配");
        }
        AuthorizationScope scope = access.authorization().getScope();
        if (scope.getKind() == AuthorizationScope.Kind.ALL) return new OrderScope(null, null);
        if (scope.getKind() == AuthorizationScope.Kind.EQUAL
            && ("customerId".equals(scope.getField()) || "merchantId".equals(scope.getField()))) {
            return new OrderScope(scope.getField(), scope.getValues().get(0).toString());
        }
        throw new PermissionDeniedException("订单动作不支持当前授权范围");
    }

    void requireInsert(Order order) {
        if (field != null && !Objects.equals(value, String.valueOf(
            "customerId".equals(field) ? order.getCustomerId() : order.getMerchantId()))) {
            throw new PermissionDeniedException("订单归属超出授权范围");
        }
    }

    void apply(LambdaQueryWrapper<Order> query) {
        if ("customerId".equals(field)) query.eq(Order::getCustomerId, value);
        if ("merchantId".equals(field)) query.eq(Order::getMerchantId, value);
    }

    void apply(LambdaUpdateWrapper<Order> update) {
        if ("customerId".equals(field)) update.eq(Order::getCustomerId, value);
        if ("merchantId".equals(field)) update.eq(Order::getMerchantId, value);
    }
}
