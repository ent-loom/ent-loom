package com.entloom.crud.starter;

import com.entloom.crud.api.model.CommandAction;
import com.entloom.crud.core.capability.command.gateway.CommandGatewayImpl;
import com.entloom.crud.core.capability.command.handler.CommandActionContract;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.execution.ExecutionPipeline;
import com.entloom.crud.core.runtime.router.DefaultCommandRouter;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TypedCommandGatewayContractTest {
    @Test
    void 调用前拒绝伪造的响应契约且不进入执行链() {
        var router = mock(DefaultCommandRouter.class);
        var pipeline = mock(ExecutionPipeline.class);
        when(router.resolveActionContract(Order.class, List.of(Order.class), "place"))
            .thenReturn(new CommandActionContract(String.class, Long.class, List.of()));
        var gateway = new CommandGatewayImpl(router, null, null, pipeline);
        var wrongDefinition = new CommandAction<String, String>(Order.class, "place", String.class, String.class) { };
        assertThrows(ValidationException.class, () -> gateway.action(wrongDefinition, "订单"));
        verifyNoInteractions(pipeline);
    }
    static class Order { }
}
