package com.entloom.crud.core.capability.command.gateway;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandAction;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.CommandActionRejectedException;
import com.entloom.crud.api.enums.CrudErrorCode;
import com.entloom.crud.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypedCommandActionTest {
    private static final CommandAction<String, Long> ACTION = new TestAction();

    @Test
    void 类型化调用经原网关执行并拆包业务结果() {
        RecordingGateway gateway = new RecordingGateway(CommandResult.success(42L));
        Long id = gateway.action(ACTION, "订单");
        assertEquals(Long.valueOf(42L), id);
        assertEquals(Order.class, gateway.spec.getRootType());
        assertEquals("place", gateway.spec.getScene());
        assertEquals(CommandOperation.ACTION, gateway.spec.getOp());
        assertEquals("订单", gateway.spec.getPayload());
        assertEquals(CommandResult.class, gateway.spec.getResultType());
    }

    @Test
    void 业务异常原样传播() {
        final IllegalStateException expected = new IllegalStateException("库存不足");
        CommandGateway gateway = new CommandGateway() {
            public <P, R> R action(CommandSpec<P> spec) { throw expected; }
        };
        assertSame(expected, assertThrows(IllegalStateException.class, () -> gateway.action(ACTION, "订单")));
    }

    @Test
    void 失败或错误类型结果不能作为成功数据返回() {
        assertThrows(CrudException.class, () -> new RecordingGateway(new CommandResult<Long>()).action(ACTION, "订单"));
        assertThrows(CrudException.class, () -> new RecordingGateway(null).action(ACTION, "订单"));
        assertThrows(CrudException.class, () -> new RecordingGateway(CommandResult.success("错误结果")).action(ACTION, "订单"));
    }

    @Test
    void 两种调用均抛出业务拒绝并保留原业务码和消息() {
        CommandResult<Long> result = new CommandResult<Long>();
        result.setCode("ORDER_REJECTED");
        result.setMessage("订单被拒绝");
        result.setIdempotentReplay(true);
        RecordingGateway gateway = new RecordingGateway(result);
        CommandActionRejectedException rejection = assertThrows(CommandActionRejectedException.class,
            () -> gateway.actionResult(ACTION, "订单"));
        assertEquals("ORDER_REJECTED", rejection.getCode());
        assertEquals(CrudErrorCode.BUSINESS_REJECTED, rejection.getErrorCode());
        CrudException failure = assertThrows(CrudException.class, () -> gateway.action(ACTION, "订单"));
        assertEquals("ORDER_REJECTED", failure.getReason());
        assertEquals("订单被拒绝", failure.getMessage());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void 原始类型调用也不能传入错误载荷() {
        RecordingGateway gateway = new RecordingGateway(CommandResult.success(42L));
        assertThrows(ValidationException.class, () -> gateway.action((CommandAction) ACTION, 1));
        assertNull(gateway.spec);
    }

    static class TestAction extends CommandAction<String, Long> {
        TestAction() { super(Order.class, " PLACE ", String.class, Long.class); }
    }
    static class Order { }

    static class RecordingGateway implements CommandGateway {
        final Object result;
        CommandSpec<?> spec;
        RecordingGateway(Object result) { this.result = result; }
        @SuppressWarnings("unchecked")
        public <P, R> R action(CommandSpec<P> spec) {
            this.spec = spec;
            return (R) result;
        }
    }
}
