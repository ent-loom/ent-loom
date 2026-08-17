package com.entloom.crud.core.foundation.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.entloom.crud.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

class DirectCrudWriteTransactionExecutorTest {
    @Test
    void nonePolicyExecutesWithoutTransactionManager() {
        String result = new DirectCrudWriteTransactionExecutor().execute(
            CrudWriteTransactionPolicy.NONE,
            () -> "ok"
        );

        assertEquals("ok", result);
    }

    @Test
    void transactionPoliciesFailFastWithoutTransactionManager() {
        DirectCrudWriteTransactionExecutor executor = new DirectCrudWriteTransactionExecutor();

        assertThrows(
            ValidationException.class,
            () -> executor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, () -> "not-called")
        );
        assertThrows(
            ValidationException.class,
            () -> executor.execute(CrudWriteTransactionPolicy.PER_BATCH, () -> "not-called")
        );
    }
}
