package com.entloom.crud.starter.config.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SpringCrudWriteTransactionExecutorTest {
    @Test
    void 批量回调必须提交完成后返回且拒绝外层事务与部分成功() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        SpringCrudWriteTransactionExecutor executor = new SpringCrudWriteTransactionExecutor(transactionManager);
        assertTrue(executor.supportsBatchAtomicity(EntityDaoBatchAtomicity.ALL_OR_NOTHING));
        assertFalse(executor.supportsBatchAtomicity(EntityDaoBatchAtomicity.EXPLICIT_PARTIAL));
        assertEquals("committed", executor.executeCommittedBatch(() -> {
            assertEquals(0, transactionManager.commits);
            return "committed";
        }));
        assertEquals(1, transactionManager.commits);
        assertThrows(IllegalStateException.class, () -> executor.executeCommittedBatch(() -> {
            throw new IllegalStateException("写入失败");
        }));
        assertEquals(1, transactionManager.rollbacks);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(ValidationException.class, () -> executor.executeCommittedBatch(() -> "不能执行"));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertEquals(2, transactionManager.propagationBehaviors.size());
    }

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
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            propagationBehaviors.add(Integer.valueOf(definition.getPropagationBehavior()));
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
