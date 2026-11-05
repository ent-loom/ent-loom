package com.example.minicommerce.order;

import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import static com.example.minicommerce.order.action.CommerceOrderActions.PLACE_ORDER;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.web.CommerceOrderController;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommerceOrderControllerTest {
    @Test
    void 下单响应保留失败状态业务码消息及重放标记() {
        CommandGateway gateway = mock(CommandGateway.class);
        CommandResult<PlaceOrderResult> result = new CommandResult<>();
        result.setSuccess(false);
        result.setCode("ORDER_REJECTED");
        result.setMessage("订单被拒绝");
        result.setIdempotentReplay(true);
        when(gateway.actionResult(eq(PLACE_ORDER), any(PlaceOrderCommand.class))).thenReturn(result);
        var response = new CommerceOrderController(gateway).place(new PlaceOrderCommand(List.of()));
        assertFalse(response.isSuccess());
        assertEquals("ORDER_REJECTED", response.getCode());
        assertEquals("订单被拒绝", response.getMessage());
        assertEquals(true, response.getMeta().get("idempotentReplay"));
    }
}
