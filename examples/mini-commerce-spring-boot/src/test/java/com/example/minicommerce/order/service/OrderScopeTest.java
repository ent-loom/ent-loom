package com.example.minicommerce.order.service;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.example.minicommerce.order.entity.Order;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderScopeTest {
    @Test
    void 拒绝其他资源操作和无法落实的范围() {
        var access = mock(AuthorizedActionContext.class);
        var result = mock(AuthorizationResult.class);
        var request = mock(AuthorizationRequest.class);
        doReturn(Order.class).when(access).entityType();
        when(access.authorization()).thenReturn(result);
        when(result.getRequest()).thenReturn(request);
        when(request.getOperation()).thenReturn(new BusinessOperation("order", "cancel"));
        for (AuthorizationScope scope : List.of(AuthorizationScope.none(), AuthorizationScope.in("customerId", List.of(1, 2)),
                AuthorizationScope.equal("status", "CREATED"), AuthorizationScope.and(List.of(AuthorizationScope.all())))) {
            when(result.getScope()).thenReturn(scope);
            assertThrows(PermissionDeniedException.class, () -> OrderScope.from(access, Order.CANCEL));
        }
        when(result.getScope()).thenReturn(AuthorizationScope.all());
        assertThrows(PermissionDeniedException.class, () -> OrderScope.from(access, Order.CREATE));
        when(request.getOperation()).thenReturn(new BusinessOperation("other", "cancel"));
        assertThrows(PermissionDeniedException.class, () -> OrderScope.from(access, Order.CANCEL));
        when(request.getOperation()).thenReturn(new BusinessOperation("order", "cancel"));
        doReturn(String.class).when(access).entityType();
        assertThrows(PermissionDeniedException.class, () -> OrderScope.from(access, Order.CANCEL));
    }

    @Test
    void 创建订单必须满足授权归属() {
        var access = mock(AuthorizedActionContext.class);
        var result = mock(AuthorizationResult.class);
        var request = mock(AuthorizationRequest.class);
        doReturn(Order.class).when(access).entityType();
        when(access.authorization()).thenReturn(result);
        when(result.getRequest()).thenReturn(request);
        when(request.getOperation()).thenReturn(new BusinessOperation("order", "create"));
        when(result.getScope()).thenReturn(AuthorizationScope.equal("customerId", 301));
        var scope = OrderScope.from(access, Order.CREATE);
        var order = new Order();
        order.setCustomerId(302L);
        assertThrows(PermissionDeniedException.class, () -> scope.requireInsert(order));
        order.setCustomerId(301L);
        scope.requireInsert(order);
    }
}
