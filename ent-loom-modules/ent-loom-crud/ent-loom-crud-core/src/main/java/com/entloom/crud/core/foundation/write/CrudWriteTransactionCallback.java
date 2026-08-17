package com.entloom.crud.core.foundation.write;

/**
 * CRUD 写入事务中的操作回调。
 */
@FunctionalInterface
public interface CrudWriteTransactionCallback<T> {
    T execute();
}
