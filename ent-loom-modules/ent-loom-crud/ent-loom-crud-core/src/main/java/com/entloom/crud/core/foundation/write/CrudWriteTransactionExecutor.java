package com.entloom.crud.core.foundation.write;

/**
 * CRUD 写入事务边界 SPI。
 */
public interface CrudWriteTransactionExecutor {
    <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback);
    /** 回调绑定最终事务完成状态；加入外层事务时延迟到其提交或回滚，未适配的实现明确拒绝。 */
    default <T> T executeObserved(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback,
        java.util.function.Consumer<Boolean> completion) {
        throw new com.entloom.crud.core.exception.ValidationException("事务执行器尚不支持最终完成通知");
    }
}
