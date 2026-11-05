package com.entloom.crud.starter.config.write;

import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.foundation.write.CrudBatchTransactionCapability;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 基于 Spring 事务管理器的 CRUD 写入事务实现。
 */
public final class SpringCrudWriteTransactionExecutor implements CrudWriteTransactionExecutor, CrudBatchTransactionCapability {
    private final TransactionTemplate requiredTransactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public SpringCrudWriteTransactionExecutor(PlatformTransactionManager transactionManager) {
        PlatformTransactionManager actual = Objects.requireNonNull(transactionManager, "transactionManager 不能为空");
        this.requiredTransactionTemplate = new TransactionTemplate(actual);
        this.requiredTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.requiresNewTransactionTemplate = new TransactionTemplate(actual);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean supportsBatchAtomicity(EntityDaoBatchAtomicity atomicity) {
        return atomicity == EntityDaoBatchAtomicity.ALL_OR_NOTHING;
    }

    @Override
    public <T> T executeCommittedBatch(CrudWriteTransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "批量事务回调不能为空");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ValidationException("批量原子写入不允许外层事务，必须拥有最终提交边界");
        }
        return requiresNewTransactionTemplate.execute(status -> callback.execute());
    }

    @Override
    public <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback 不能为空");
        if (policy == null || policy == CrudWriteTransactionPolicy.NONE) {
            return callback.execute();
        }
        if (policy == CrudWriteTransactionPolicy.SINGLE_TRANSACTION) {
            return requiredTransactionTemplate.execute(status -> callback.execute());
        }
        if (policy == CrudWriteTransactionPolicy.PER_BATCH) {
            return requiresNewTransactionTemplate.execute(status -> callback.execute());
        }
        throw new IllegalArgumentException("不支持的写入事务策略: " + policy);
    }
    @Override
    public <T> T executeObserved(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback,
        java.util.function.Consumer<Boolean> completion) {
        Objects.requireNonNull(completion, "事务完成通知不能为空");
        return execute(policy, () -> {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
                throw new ValidationException("最终完成通知必须绑定实际事务");
            TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    completion.accept(status == STATUS_COMMITTED);
                }
            });
            return callback.execute();
        });
    }

}
