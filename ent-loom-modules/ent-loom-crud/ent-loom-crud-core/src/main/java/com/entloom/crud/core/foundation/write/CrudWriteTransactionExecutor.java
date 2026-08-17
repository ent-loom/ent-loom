package com.entloom.crud.core.foundation.write;

/**
 * CRUD 写入事务边界 SPI。
 */
public interface CrudWriteTransactionExecutor {
    <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback);
}
