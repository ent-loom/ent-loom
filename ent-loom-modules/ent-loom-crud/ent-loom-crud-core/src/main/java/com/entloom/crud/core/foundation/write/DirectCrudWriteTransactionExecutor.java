package com.entloom.crud.core.foundation.write;

import com.entloom.crud.core.exception.ValidationException;
import java.util.Objects;

/**
 * 未接入事务管理器时的直接执行实现，保留核心模块的轻量运行能力。
 */
public final class DirectCrudWriteTransactionExecutor implements CrudWriteTransactionExecutor {
    @Override
    public <T> T execute(CrudWriteTransactionPolicy policy, CrudWriteTransactionCallback<T> callback) {
        CrudWriteTransactionCallback<T> actual = Objects.requireNonNull(callback, "callback 不能为空");
        if (policy == null || policy == CrudWriteTransactionPolicy.NONE) {
            return actual.execute();
        }
        throw new ValidationException("事务策略 " + policy + " 需要配置 PlatformTransactionManager");
    }
}
