package com.entloom.crud.starter.config.write;

import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring 事务管理器的 CRUD 写入事务实现。
 */
public final class SpringCrudWriteTransactionExecutor implements CrudWriteTransactionExecutor {
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
}
