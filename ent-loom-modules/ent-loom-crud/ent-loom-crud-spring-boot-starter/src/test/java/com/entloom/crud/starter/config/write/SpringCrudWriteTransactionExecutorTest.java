package com.entloom.crud.starter.config.write;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SpringCrudWriteTransactionExecutorTest {
    @Test
    void perBatchUsesRequiresNewAndSingleTransactionUsesRequired() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        SpringCrudWriteTransactionExecutor executor = new SpringCrudWriteTransactionExecutor(transactionManager);

        assertEquals("single", executor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, () -> "single"));
        assertEquals("batch", executor.execute(CrudWriteTransactionPolicy.PER_BATCH, () -> "batch"));
        assertEquals("none", executor.execute(CrudWriteTransactionPolicy.NONE, () -> "none"));
        assertEquals(
            Arrays.asList(
                TransactionDefinition.PROPAGATION_REQUIRED,
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
            ),
            transactionManager.propagationBehaviors
        );
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        private final List<Integer> propagationBehaviors = new ArrayList<Integer>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            propagationBehaviors.add(Integer.valueOf(definition.getPropagationBehavior()));
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
