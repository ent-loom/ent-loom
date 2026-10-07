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
