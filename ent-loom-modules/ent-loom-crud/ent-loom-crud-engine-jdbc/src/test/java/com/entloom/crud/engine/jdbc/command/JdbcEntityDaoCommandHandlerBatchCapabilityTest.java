package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.handler.CrudCommandHandler;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.engine.jdbc.test.entity.OrderTestEntity;
import com.entloom.crud.engine.jdbc.test.support.EngineJdbcTestSupport;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcEntityDaoCommandHandlerBatchCapabilityTest extends EngineJdbcTestSupport {
    @Test
    void batch_requires_declared_atomicity_before_transaction_callback() {
        AtomicBoolean callbackCalled = new AtomicBoolean();
        CrudWriteTransactionExecutor unsupported = new CrudWriteTransactionExecutor() {
            @Override
            public <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback) {
                callbackCalled.set(true);
                return callback.execute();
            }
        };
        CrudCommandHandler<Object, Object> fallback = new CrudCommandHandler<Object, Object>() {
            @Override
            public boolean supports(CommandSpec<Object> spec) {
                return false;
            }

            @Override
            public Object action(CommandSpec<Object> spec) {
                return null;
            }

            @Override
            public Object create(CommandSpec<Object> spec) {
                return null;
            }

            @Override
            public Object update(CommandSpec<Object> spec) {
                return null;
            }

            @Override
            public Object delete(CommandSpec<Object> spec) {
                return null;
            }

            @Override
            public Object batch(CommandSpec<Object> spec) {
                return null;
            }
        };
        JdbcEntityDaoCommandHandler<Object, Object> handler = new JdbcEntityDaoCommandHandler<Object, Object>(
            metaRegistry, entityDaoFactory, fallback, unsupported
        );

        ValidationException exception = assertThrows(ValidationException.class,
            () -> handler.action(batchSpec()));

        assertFalse(callbackCalled.get());
        assertTrue(exception.getMessage().contains("未声明支持批量原子性"));
        assertFalse(rowExists(3101L));
    }

    private CommandSpec<Object> batchSpec() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("orderNo", "CAPABILITY-GATE");
        values.put("isDeleted", 0);
        BatchCommand<Object> batch = BatchCommand.of(Collections.singletonList(
            new WriteCommand<Object>(CommandOperation.CREATE, 3101L, values)
        ));
        return CommandSpec.<Object>builder()
            .rootType(OrderTestEntity.class)
            .resultType(CommandResult.class)
            .op(CommandOperation.CREATE_BATCH)
            .payload(batch)
            .build();
    }

    private boolean rowExists(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(1) from t_order where id = ?", Integer.class, id
        );
        return count != null && count.intValue() > 0;
    }
}
