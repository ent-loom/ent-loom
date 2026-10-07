package com.entloom.crud.engine.jdbc.transaction;

import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudBatchTransactionCapability;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 JDBC 数据源的 CRUD 写入事务执行器。
 */
public final class JdbcCrudWriteTransactionExecutor
    implements CrudWriteTransactionExecutor, CrudBatchTransactionCapability {
    private final TransactionTemplate requiredTransactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public JdbcCrudWriteTransactionExecutor(DataSource dataSource) {
        DataSource actual = Objects.requireNonNull(dataSource, "dataSource 不能为空");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(actual);
        this.requiredTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiredTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
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

    @Override
    public boolean supportsBatchAtomicity(EntityDaoBatchAtomicity atomicity) {
        return atomicity == EntityDaoBatchAtomicity.ALL_OR_NOTHING;
    }

    @Override
    public <T> T executeCommittedBatch(CrudWriteTransactionCallback<T> callback) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ValidationException(
                "逐目标批量写入必须拥有最终提交边界，不能嵌套在外层事务中"
            );
        }
        return execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, callback);
    }
}
