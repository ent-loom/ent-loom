package com.entloom.crud.engine.jdbc.transaction;

import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCrudWriteTransactionExecutorTest {
    @Test
    void 事务完成通知等待外层最终提交或回滚() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:observed_transaction", "sa", "");
        var executor = new JdbcCrudWriteTransactionExecutor(source);
        var outer = new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        var outcomes = new java.util.ArrayList<Boolean>();
        outer.executeWithoutResult(status -> {
            executor.executeObserved(com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> "业务结果", outcomes::add);
            assertTrue(outcomes.isEmpty());
            status.setRollbackOnly();
        });
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(false), outcomes);
        outcomes.clear();
        outer.executeWithoutResult(status -> {
            executor.executeObserved(com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> "业务结果", outcomes::add);
            assertTrue(outcomes.isEmpty());
        });
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(true), outcomes);
    }

    @Test
    void declares_only_all_or_nothing_batch_atomicity() {
        JdbcCrudWriteTransactionExecutor executor = new JdbcCrudWriteTransactionExecutor(
            new DriverManagerDataSource("jdbc:h2:mem:batch_capability_test", "sa", "")
        );

        assertTrue(EntityDaoBatchAtomicity.ALL_OR_NOTHING.isSupportedBy(executor));
        assertFalse(EntityDaoBatchAtomicity.EXPLICIT_PARTIAL.isSupportedBy(executor));
        EntityDaoBatchAtomicity.ALL_OR_NOTHING.requireSupportedBy(executor);
        assertThrows(ValidationException.class,
            () -> EntityDaoBatchAtomicity.EXPLICIT_PARTIAL.requireSupportedBy(executor));
    }
}
