package com.example.minicommerce.order.service.command;

import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessOperation;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.example.minicommerce.order.entity.CommerceOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CommerceOrderCommandServiceTest {
    @Test
    void 拒绝其他实体资源和操作() {
        var access = mock(AuthorizedActionContext.class);
        var result = mock(AuthorizationResult.class);
        var request = mock(AuthorizationRequest.class);
        doReturn(CommerceOrder.class).when(access).entityType();
        when(access.authorization()).thenReturn(result);
        when(result.getRequest()).thenReturn(request);
        when(request.getOperation()).thenReturn(new BusinessOperation("commerceOrder", "cancel"));
        var service = new CommerceOrderCommandService(null, null, null, null);
        assertThrows(PermissionDeniedException.class, () -> service.place(access, null));
        when(request.getOperation()).thenReturn(new BusinessOperation("other", "cancel"));
        assertThrows(PermissionDeniedException.class, () -> service.cancel(access, 1L));
        when(request.getOperation()).thenReturn(new BusinessOperation("commerceOrder", "cancel"));
        doReturn(String.class).when(access).entityType();
        assertThrows(PermissionDeniedException.class, () -> service.cancel(access, 1L));
    }

}
