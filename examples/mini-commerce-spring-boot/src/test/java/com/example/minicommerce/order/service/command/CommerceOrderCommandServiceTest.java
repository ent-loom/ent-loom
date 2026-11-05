package com.example.minicommerce.order.service.command;

import static com.example.minicommerce.order.action.CommerceOrderActions.CANCEL_ORDER;
import static com.example.minicommerce.order.action.CommerceOrderActions.PLACE_ORDER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;

class CommerceOrderCommandServiceTest {
    @Test
    void 下单和取消必须先通过对应类型化动作校验() {
        var access = mock(AuthorizedActionContext.class);
        doThrow(new PermissionDeniedException("动作不匹配")).when(access).requireAction(PLACE_ORDER);
        doThrow(new PermissionDeniedException("动作不匹配")).when(access).requireAction(CANCEL_ORDER);
        var service = new CommerceOrderCommandService(null, null, null, null);
        assertThrows(PermissionDeniedException.class, () -> service.place(access, null));
        assertThrows(PermissionDeniedException.class, () -> service.cancel(access, 1L));
        verify(access).requireAction(PLACE_ORDER);
        verify(access).requireAction(CANCEL_ORDER);
        verifyNoMoreInteractions(access);
    }
}
